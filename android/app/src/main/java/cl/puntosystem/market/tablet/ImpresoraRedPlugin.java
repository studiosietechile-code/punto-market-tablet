package cl.puntosystem.market.tablet;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.json.JSONException;

/**
 * Puente entre pos-market.html (el mismo archivo, sin cambios de lógica,
 * que ya corre en Windows/Mac) y una impresora térmica conectada a la
 * red local — por cable o WiFi, da lo mismo, porque en ambos casos el
 * programa le habla directo a su dirección IP.
 *
 * Por qué existe esto: un navegador normal (y por lo tanto una página
 * web corriendo dentro de un WebView común) NO tiene permiso de abrir
 * una conexión directa (un "socket") a otro dispositivo de la red, por
 * seguridad — es una restricción de todos los navegadores, no de este
 * programa. Por eso la versión tablet necesita ser una aplicación
 * instalada de verdad: este plugin es la única pieza que efectivamente
 * abre esa conexión, usando el puerto 9100, el estándar que usan casi
 * todas las impresoras térmicas con entrada de red ("impresión RAW" /
 * "ESC/POS por red").
 *
 * En Windows, el mismo trabajo lo hace electron-updater... no, perdón:
 * lo hace el módulo "usb" hablando directo por USB (ver main.js,
 * printRawUsb/printRawEscpos). Esta es la versión equivalente para
 * tablet, hablando por red en vez de por cable USB.
 */
@CapacitorPlugin(name = "ImpresoraRed")
public class ImpresoraRedPlugin extends Plugin {

  private static final String PREFS = "pmkt_impresora_red";
  private static final String KEY_IP = "ip";
  private static final String KEY_PORT = "port";

  // Tiempo máximo de espera al conectar — si la impresora está apagada o
  // la IP está mal escrita, no queremos dejar a la cajera esperando para
  // siempre: a los 4 segundos se avisa que algo falló.
  private static final int TIMEOUT_CONEXION_MS = 4000;

  @PluginMethod
  public void printRawNetwork(PluginCall call) {
    String ip = call.getString("ip");
    Integer port = call.getInt("port");
    JSArray bytesArray = call.getArray("bytes");

    if (ip == null || ip.trim().isEmpty()) {
      fallar(call, "Falta la dirección IP de la impresora.");
      return;
    }
    if (port == null) port = 9100; // puerto estándar de impresión RAW por red
    final String ipFinal = ip.trim();
    final int portFinal = port;

    final byte[] bytes;
    try {
      bytes = aBytes(bytesArray);
    } catch (JSONException e) {
      fallar(call, "No se pudo leer el contenido a imprimir: " + e.getMessage());
      return;
    }

    // La conexión de red nunca puede hacerse en el hilo principal
    // (Android lo prohíbe y cerraría la aplicación), así que corre en
    // un hilo aparte — exactamente igual de "en segundo plano" que la
    // impresión por USB en la versión de Windows.
    new Thread(() -> {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(ipFinal, portFinal), TIMEOUT_CONEXION_MS);
        OutputStream salida = socket.getOutputStream();
        salida.write(bytes);
        salida.flush();
        JSObject resultado = new JSObject();
        resultado.put("success", true);
        call.resolve(resultado);
      } catch (Exception e) {
        fallar(call, describirError(e, ipFinal, portFinal));
      }
    }).start();
  }

  @PluginMethod
  public void testNetworkPrinter(PluginCall call) {
    String ip = call.getString("ip");
    Integer port = call.getInt("port");
    if (ip == null || ip.trim().isEmpty()) {
      fallar(call, "Falta la dirección IP de la impresora.");
      return;
    }
    final String ipFinal = ip.trim();
    final int portFinal = (port == null) ? 9100 : port;

    new Thread(() -> {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(ipFinal, portFinal), TIMEOUT_CONEXION_MS);
        JSObject resultado = new JSObject();
        resultado.put("success", true);
        call.resolve(resultado);
      } catch (Exception e) {
        fallar(call, describirError(e, ipFinal, portFinal));
      }
    }).start();
  }

  @PluginMethod
  public void getPrinterConfig(PluginCall call) {
    SharedPreferences prefs = prefs();
    JSObject networkPrinter = new JSObject();
    networkPrinter.put("ip", prefs.getString(KEY_IP, ""));
    int puertoGuardado = prefs.getInt(KEY_PORT, 9100);
    networkPrinter.put("port", puertoGuardado);

    JSObject resultado = new JSObject();
    // Se mantienen estos dos campos vacíos (en vez de omitirlos) para
    // calzar exactamente con la forma del objeto que ya espera
    // ConfigImpresora en pos-market.html (usbDevice/escposPrinter son
    // los modos de Windows/Mac, acá no aplican).
    resultado.put("usbDevice", "");
    resultado.put("escposPrinter", "");
    resultado.put("networkPrinter", networkPrinter);
    call.resolve(resultado);
  }

  @PluginMethod
  public void savePrinterConfig(PluginCall call) {
    JSObject networkPrinter = call.getObject("networkPrinter");
    SharedPreferences.Editor editor = prefs().edit();
    if (networkPrinter != null) {
      editor.putString(KEY_IP, networkPrinter.optString("ip", ""));
      editor.putInt(KEY_PORT, networkPrinter.optInt("port", 9100));
    }
    editor.apply();
    call.resolve();
  }

  private SharedPreferences prefs() {
    Context contexto = getContext();
    return contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private byte[] aBytes(JSArray arreglo) throws JSONException {
    if (arreglo == null) return new byte[0];
    int largo = arreglo.length();
    byte[] bytes = new byte[largo];
    for (int i = 0; i < largo; i++) {
      bytes[i] = (byte) arreglo.getInt(i);
    }
    return bytes;
  }

  private String describirError(Exception e, String ip, int port) {
    if (e instanceof java.net.UnknownHostException) {
      return "No se encontró la dirección " + ip + " en la red. Revisa que la IP esté bien escrita.";
    }
    if (e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException) {
      return "No se pudo conectar a la impresora en " + ip + ":" + port + ". Revisa que esté encendida y conectada a la misma red que la tablet.";
    }
    return "Error de conexión con la impresora: " + e.getMessage();
  }

  private void fallar(PluginCall call, String motivo) {
    JSObject resultado = new JSObject();
    resultado.put("success", false);
    resultado.put("failureReason", motivo);
    call.resolve(resultado); // resolve (no reject) — pos-market.html espera {success:false, failureReason} para mostrar el mensaje, igual que en Windows
  }
}
