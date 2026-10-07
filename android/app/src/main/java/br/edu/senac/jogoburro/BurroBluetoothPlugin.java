package br.edu.senac.jogoburro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Android-only BLE peripheral + central transport. Messages are UTF-8 JSON, limited to one BLE write. */
@CapacitorPlugin(name = "BurroBluetooth")
public class BurroBluetoothPlugin extends Plugin {
  private static final UUID SERVICE_UUID = UUID.fromString("9e1b0001-7e55-4c75-8e20-000000000001");
  private static final UUID MESSAGE_UUID = UUID.fromString("9e1b0002-7e55-4c75-8e20-000000000001");
  private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
  private BluetoothAdapter adapter;
  private BluetoothLeAdvertiser advertiser;
  private BluetoothGattServer server;
  private BluetoothGatt gatt;
  private BluetoothGattCharacteristic messageCharacteristic;
  private BluetoothLeScanner scanner;
  private final Map<String, BluetoothDevice> discovered = new HashMap<>();
  private final List<BluetoothDevice> subscribers = new ArrayList<>();

  @Override public void load() {
    BluetoothManager manager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
    adapter = manager == null ? null : manager.getAdapter();
  }

  private boolean ready(PluginCall call) {
    if (adapter == null || !adapter.isEnabled()) { call.reject("Bluetooth está desligado ou indisponível."); return false; }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) {
      getActivity().requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE}, 1881);
      call.reject("Permissões Bluetooth solicitadas. Toque novamente após aceitar."); return false;
    }
    return true;
  }
  private void event(String name, String message, String deviceId) { JSObject data = new JSObject(); data.put("message", message); data.put("deviceId", deviceId); notifyListeners(name, data); }

  @PluginMethod @SuppressLint("MissingPermission")
  public void host(PluginCall call) {
    if (!ready(call)) return;
    BluetoothManager manager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
    advertiser = adapter.getBluetoothLeAdvertiser();
    if (advertiser == null) { call.reject("Este aparelho não suporta anunciar Bluetooth LE."); return; }
    messageCharacteristic = new BluetoothGattCharacteristic(MESSAGE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_WRITE);
    messageCharacteristic.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
    BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    service.addCharacteristic(messageCharacteristic);
    server = manager.openGattServer(getContext(), serverCallback);
    if (server == null || !server.addService(service)) { call.reject("Não foi possível criar o serviço GATT."); return; }
    AdvertiseData data = new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(SERVICE_UUID)).setIncludeDeviceName(true).build();
    AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(true).build();
    advertiser.startAdvertising(settings, data, advertiseCallback);
    call.resolve();
  }

  private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
    @Override public void onStartSuccess(AdvertiseSettings settings) { event("status", "ANUNCIANDO", ""); }
    @Override public void onStartFailure(int errorCode) { event("error", "Falha ao anunciar sala BLE: " + errorCode, ""); }
  };
  private final BluetoothGattServerCallback serverCallback = new BluetoothGattServerCallback() {
    @Override public void onConnectionStateChange(BluetoothDevice device, int status, int state) { event("connection", state == BluetoothGatt.STATE_CONNECTED ? "CONECTADO" : "DESCONECTADO", device.getAddress()); }
    @Override public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) { if (CCCD_UUID.equals(descriptor.getUuid()) && Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) && !subscribers.contains(device)) subscribers.add(device); if (responseNeeded && server != null) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null); }
    @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) { if (MESSAGE_UUID.equals(characteristic.getUuid())) event("message", new String(value, StandardCharsets.UTF_8), device.getAddress()); if (responseNeeded && server != null) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null); }
  };

  @PluginMethod @SuppressLint("MissingPermission")
  public void scan(PluginCall call) {
    if (!ready(call)) return;
    scanner = adapter.getBluetoothLeScanner(); if (scanner == null) { call.reject("Scanner BLE indisponível."); return; }
    List<ScanFilter> filters = Arrays.asList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build());
    scanner.startScan(filters, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback); call.resolve();
  }
  private final ScanCallback scanCallback = new ScanCallback() {
    @Override public void onScanResult(int callbackType, ScanResult result) { BluetoothDevice d = result.getDevice(); discovered.put(d.getAddress(), d); JSObject data = new JSObject(); data.put("deviceId", d.getAddress()); data.put("name", d.getName() == null ? "Sala Burro" : d.getName()); notifyListeners("deviceFound", data); }
    @Override public void onScanFailed(int errorCode) { event("error", "Falha na busca BLE: " + errorCode, ""); }
  };

  @PluginMethod @SuppressLint("MissingPermission")
  public void connect(PluginCall call) { if (!ready(call)) return; BluetoothDevice device = discovered.get(call.getString("deviceId")); if (device == null) { call.reject("Sala não encontrada. Procure novamente."); return; } if (scanner != null) scanner.stopScan(scanCallback); gatt = device.connectGatt(getContext(), false, clientCallback); call.resolve(); }
  private final BluetoothGattCallback clientCallback = new BluetoothGattCallback() {
    @Override public void onConnectionStateChange(BluetoothGatt connection, int status, int state) { if (state == BluetoothGatt.STATE_CONNECTED) { event("connection", "CONECTADO", connection.getDevice().getAddress()); connection.discoverServices(); } else event("connection", "DESCONECTADO", connection.getDevice().getAddress()); }
    @Override public void onServicesDiscovered(BluetoothGatt connection, int status) { BluetoothGattService service = connection.getService(SERVICE_UUID); if (service == null) return; messageCharacteristic = service.getCharacteristic(MESSAGE_UUID); connection.setCharacteristicNotification(messageCharacteristic, true); BluetoothGattDescriptor cccd = messageCharacteristic.getDescriptor(CCCD_UUID); if (cccd != null) { cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); connection.writeDescriptor(cccd); } }
    @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) { if (MESSAGE_UUID.equals(characteristic.getUuid())) event("message", new String(characteristic.getValue(), StandardCharsets.UTF_8), connection.getDevice().getAddress()); }
  };

  @PluginMethod @SuppressLint("MissingPermission")
  public void send(PluginCall call) { if (!ready(call)) return; String message = call.getString("message", ""); byte[] value = message.getBytes(StandardCharsets.UTF_8); if (value.length > 480) { call.reject("Mensagem Bluetooth excede 480 bytes."); return; } if (server != null && messageCharacteristic != null) { messageCharacteristic.setValue(value); for (BluetoothDevice device : new ArrayList<>(subscribers)) server.notifyCharacteristicChanged(device, messageCharacteristic, false); call.resolve(); return; } if (gatt != null && messageCharacteristic != null) { messageCharacteristic.setValue(value); messageCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); if (gatt.writeCharacteristic(messageCharacteristic)) { call.resolve(); } else call.reject("Não foi possível enviar mensagem."); return; } call.reject("Não existe conexão Bluetooth ativa."); }
  @PluginMethod @SuppressLint("MissingPermission")
  public void sendTo(PluginCall call) { if (!ready(call)) return; String message = call.getString("message", ""); String deviceId = call.getString("deviceId", ""); if (server == null || messageCharacteristic == null) { call.reject("Somente o anfitrião pode enviar mensagem individual."); return; } if (message.getBytes(StandardCharsets.UTF_8).length > 480) { call.reject("Mensagem Bluetooth excede 480 bytes."); return; } for (BluetoothDevice device : subscribers) { if (device.getAddress().equals(deviceId)) { messageCharacteristic.setValue(message.getBytes(StandardCharsets.UTF_8)); server.notifyCharacteristicChanged(device, messageCharacteristic, false); call.resolve(); return; } } call.reject("Jogador Bluetooth não conectado."); }
  @PluginMethod @SuppressLint("MissingPermission")
  public void stop(PluginCall call) { if (scanner != null) scanner.stopScan(scanCallback); if (advertiser != null) advertiser.stopAdvertising(advertiseCallback); if (gatt != null) { gatt.close(); gatt = null; } if (server != null) { server.close(); server = null; } subscribers.clear(); call.resolve(); }
}
