package br.edu.senac.jogoburro;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(android.os.Bundle savedInstanceState) {
    registerPlugin(BurroBluetoothPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
