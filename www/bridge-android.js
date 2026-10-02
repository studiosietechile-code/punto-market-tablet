// ================================================================
// PUENTE DE PUNTO MARKET PARA TABLET (ANDROID)
// ================================================================
// Este archivo es el equivalente, para la tablet, de lo que main.js +
// preload.js hacen en las versiones de Windows y Mac: conecta el
// programa (pos-market.html, el MISMO archivo sin ningún cambio de
// lógica de venta) con lo que necesita del dispositivo físico — en este
// caso, solo la impresora de red (ver ImpresoraRedPlugin.java).
//
// A propósito define "window.electronAPI" con el mismo nombre que usa
// Electron en Windows/Mac: así pos-market.html no necesita saber en qué
// plataforma está corriendo — sigue usando exactamente las mismas
// comprobaciones "if (window.electronAPI && window.electronAPI.algo)"
// que ya tenía. Acá solo se define lo que existe en tablet
// (printRawNetwork/testNetworkPrinter/getPrinterConfig/savePrinterConfig);
// todo lo demás (USB, actualizaciones automáticas, pantalla completa,
// etc.) queda sin definir a propósito, igual que ya pasa hoy en Mac con
// "buscarActualizaciones" — pos-market.html ya sabe esconder/omitir esas
// funciones cuando no existen.
(function () {
  function conectarPlugin() {
    if (!window.Capacitor || !window.Capacitor.registerPlugin) {
      // El puente nativo de Capacitor se inyecta antes de que corra
      // cualquier script de la página, así que esto no debería pasar
      // nunca en la práctica — se deja como red de seguridad en vez de
      // dejar la tablet sin impresión si algún día cambia ese orden.
      setTimeout(conectarPlugin, 50);
      return;
    }

    const ImpresoraRed = window.Capacitor.registerPlugin('ImpresoraRed');

    window.electronAPI = {
      async printRawNetwork(ip, port, bytes) {
        try {
          return await ImpresoraRed.printRawNetwork({ ip, port, bytes });
        } catch (e) {
          return { success: false, failureReason: (e && e.message) || String(e) };
        }
      },
      async testNetworkPrinter(ip, port) {
        try {
          return await ImpresoraRed.testNetworkPrinter({ ip, port });
        } catch (e) {
          return { success: false, failureReason: (e && e.message) || String(e) };
        }
      },
      async getPrinterConfig() {
        try {
          return await ImpresoraRed.getPrinterConfig();
        } catch (e) {
          console.error('getPrinterConfig falló:', e);
          return { usbDevice: '', escposPrinter: '', networkPrinter: { ip: '', port: 9100 } };
        }
      },
      async savePrinterConfig(cfg) {
        return await ImpresoraRed.savePrinterConfig(cfg);
      },
    };
  }

  conectarPlugin();
})();
