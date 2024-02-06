package com.example.gpsmusic;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.location.Location;
import android.location.LocationListener;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private BluetoothAdapter bluetoothAdapter;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private BluetoothSocket bluetoothSocket;
    UUID uuid;
    String uuidString;
    private MediaPlayer mediaPlayer;
    TextView speedView;
    Button play_btn;
    Button stop_btn;

    private float speedKmH;

    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)) {

        } else {

            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        speedView = findViewById(R.id.GPS);
        play_btn = findViewById(R.id.play);
        stop_btn = findViewById(R.id.stop);
        uuid = UUID.randomUUID();


        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {

                float speed = location.getSpeed();
                speedKmH = speed * 3.6f;
                Log.d("GPSMusic", "Velocidade: " + speedKmH);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        speedView.setText(String.format("%.2f km/h", speedKmH));
                    }
                });

            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }

        };




        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
        uuidString = uuid.toString();
        mediaPlayer = new MediaPlayer();

        for (BluetoothDevice device : pairedDevices) {
            deviceList.add(device);
        }

        if (bluetoothAdapter == null) {
            // O dispositivo não suporta Bluetooth
            // Lide com isso de acordo com as necessidades do seu aplicativo
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                // O Bluetooth está desativado, solicite ao usuário que o ative
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

            }
        }


        requestLocationPermission();


        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        play_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BluetoothDevice device = getFirstPairedDevice();
                if (device != null) {
                    connectToDevice(device);
                }
            }
        });
        stop_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMusicPlayback();
            }
        });

    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            // Verificar permissão antes de estabelecer a conexão Bluetooth
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_ENABLE_BT);
                return;
            }

            // Criar um socket Bluetooth seguro
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect(); // Tentativa de conexão

            // Iniciar uma thread para receber dados do Bluetooth
            Thread receiveThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream inputStream = bluetoothSocket.getInputStream();
                        byte[] buffer = new byte[1024]; // Buffer para armazenar dados recebidos
                        int bytesRead;

                        // Loop para ler dados continuamente
                        while (true) {
                            bytesRead = inputStream.read(buffer);
                            if (bytesRead == -1) {
                                break; // Fim da transmissão
                            }

                            // Reproduzir os dados de música recebidos
                            startMusicPlayback(Arrays.copyOf(buffer, bytesRead));
                        }
                    } catch (IOException e) {
                        // Tratamento de erros na leitura de dados Bluetooth
                        e.printStackTrace();
                    } finally {
                        // Garantir que o socket seja fechado após o término da transmissão
                        closeBluetoothSocket();
                    }
                }
            });
            receiveThread.start(); // Iniciar a thread de recepção

        } catch (IOException e) {
            // Tratar exceções relacionadas à conexão Bluetooth
            e.printStackTrace();
        }
    }

    private void closeBluetoothSocket() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void startMusicPlayback(byte[] musicData) {
        try {
            // Cria um arquivo temporário
            File tempFile = File.createTempFile("tempMusic", ".tmp", getCacheDir());
            tempFile.deleteOnExit();

            // Escreve os dados no arquivo
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(musicData);
            }

            // Prepara o MediaPlayer para reproduzir o arquivo
            mediaPlayer.reset();
            mediaPlayer.setDataSource(tempFile.getAbsolutePath());
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mediaPlayer.start();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            // Trate os erros apropriadamente
        }
    }


    private void stopMusicPlayback() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates();
                } else {
                    // Informar ao usuário que a permissão foi negada.
                }
                break;
            case REQUEST_ENABLE_BT:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Bluetooth agora pode ser utilizado.
                } else {
                    // Informar ao usuário que a permissão de Bluetooth foi negada.
                }
                break;
        }
    }


    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }

    private BluetoothDevice getFirstPairedDevice() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // Solicitar a permissão Bluetooth ao usuário
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_ENABLE_BT); // Certifique-se de que REQUEST_ENABLE_BT é um código de solicitação constante definido em sua classe
            return null;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (!pairedDevices.isEmpty()) {
            // Retorna o primeiro dispositivo da lista de dispositivos emparelhados
            return pairedDevices.iterator().next();
        }
        return null;
    }



}