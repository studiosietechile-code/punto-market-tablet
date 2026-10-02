package cl.puntosystem.market.tablet;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

// Se registra acá, ANTES de super.onCreate(...), el plugin propio que le
// permite a Punto Market (el mismo pos-market.html que ya corre en
// Windows/Mac, sin cambios de lógica) hablar directo con la impresora
// térmica de red — ver ImpresoraRedPlugin.java para el detalle completo.
public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(ImpresoraRedPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
