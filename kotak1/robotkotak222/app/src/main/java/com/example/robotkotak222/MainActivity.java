package com.example.robotkotak222;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {

    private ViewModel vm;
    private TextView statusText, statusDot, cmdText, arahText;
    private EditText ipInput, portInput;
    private Button connectBtn;
    private JoystickView joyMove;

    private boolean connected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        setContentView(R.layout.activity_main);

        // Init Komponen UI
        statusText = (TextView) findViewById(R.id.status_text);
        statusDot = (TextView) findViewById(R.id.status_dot);
        cmdText = (TextView) findViewById(R.id.cmd_text);
        arahText = (TextView) findViewById(R.id.arah_text);
        ipInput = (EditText) findViewById(R.id.ip_input);
        portInput = (EditText) findViewById(R.id.port_input);
        connectBtn = (Button) findViewById(R.id.connect_btn);
        joyMove = (JoystickView) findViewById(R.id.joystick_move);

        // Init ViewModel
        vm = new ViewModel();

        vm.setStatusListener(new StatusListener() {
            @Override
            public void onStatusChanged(final String message, final boolean isConnected) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setStatus(message, isConnected);
                    }
                });
            }
        });

        vm.setFrameListener(new FrameListener() {
            @Override
            public void onFrame(final String frame) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cmdText != null) {
                            cmdText.setText(frame);
                        }
                        if (arahText != null) {
                            String[] p = frame.split(",");
                            if (p.length >= 7) {
                                String x = p[0];
                                String y = p[1];
                                String arah = p[2];
                                String putar = p[3];
                                String text = arah + " (X:" + x + ", Y:" + y + ")";
                                if (!putar.equals("NONE")) {
                                    text = arah.equals("STOP") ? putar : text + "\n" + putar;
                                }
                                arahText.setText(text);
                            }
                        }
                    }
                });
            }
        });

        if (connectBtn != null) {
            connectBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (connected) {
                        vm.disconnect();
                        return;
                    }
                    String ip = "192.168.4.1";
                    if (ipInput != null && ipInput.getText() != null) {
                        String s = ipInput.getText().toString().trim();
                        if (!s.isEmpty()) ip = s;
                    }

                    int port = 8888;
                    if (portInput != null && portInput.getText() != null) {
                        String s = portInput.getText().toString().trim();
                        if (!s.isEmpty()) {
                            try {
                                port = Integer.parseInt(s);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    setStatus("Menghubungkan ke " + ip + "...", false);
                    vm.connect(ip, port, findWifiNetwork());
                }
            });
        }

        if (joyMove != null) {
            joyMove.setOnJoystickChangeListener(new JoystickView.OnJoystickChangeListener() {
                @Override
                public void onStateChanged(float x, float y) {
                    vm.setMove(x, y);
                }
            });
        }

        // Tombol Capit (Grip) di Panel Kiri
        bindGripButton(R.id.grip_close_btn, 1);
        bindGripButton(R.id.grip_open_btn, -1);



        // Tombol D-Pad 4 Arah (Atas, Bawah, Kiri, Kanan) di Panel Kanan
        bindDpadFullButton(R.id.btn_dpad_up, 0f, 0f, 1f, 0);       // Atas: Forward
        bindDpadFullButton(R.id.btn_dpad_down, 0f, 0f, -1f, 0);   // Bawah: Backward
        bindDpadFullButton(R.id.btn_dpad_left, -1f, 0f, 0f, 0);    // Kiri: Putar kiri (anticlockwise)
        bindDpadFullButton(R.id.btn_dpad_right, 1f, 0f, 0f, 0);    // Kanan: Putar kanan (clockwise)

        View stopBtn = findViewById(R.id.stop_btn);
        if (stopBtn != null) {
            stopBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vm.stop();
                }
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindGripButton(int resId, final int dir) {
        View v = findViewById(resId);
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        vm.setGrip(dir);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        vm.setGrip(0);
                        break;
                }
                return true;
            }
        });
    }



    @SuppressLint("ClickableViewAccessibility")
    private void bindDpadFullButton(int resId, final float turnVal, final float moveX, final float moveY, final int liftDir) {
        View v = findViewById(resId);
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        if (turnVal != 0f) vm.setTurn(turnVal);
                        if (moveX != 0f || moveY != 0f) vm.setMove(moveX, moveY);
                        if (liftDir != 0) vm.setLift(liftDir);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        if (turnVal != 0f) vm.setTurn(0f);
                        if (moveX != 0f || moveY != 0f) vm.setMove(0f, 0f);
                        if (liftDir != 0) vm.setLift(0);
                        break;
                }
                return true;
            }
        });
    }

    private void setStatus(String message, boolean isConnected) {
        connected = isConnected;
        if (statusText != null) statusText.setText(message);
        if (statusDot != null) {
            statusDot.setTextColor(isConnected ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        }
        if (connectBtn != null) {
            connectBtn.setText(isConnected ? "Putus WiFi" : "Hubungkan WiFi");
        }
    }

    /**
     * Paksa socket ke ESP lewat WiFi jika seluler aktif.
     */
    @SuppressWarnings("deprecation")
    private Network findWifiNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return n;
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        vm.release();
    }

    // ---------- Handler Stik Gamepad Fisik (Bluetooth / USB OTG) ----------

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int src = event.getSource();
        if ((src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK || (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float x = event.getAxisValue(MotionEvent.AXIS_X);
                float y = -event.getAxisValue(MotionEvent.AXIS_Y);
                float turn = event.getAxisValue(MotionEvent.AXIS_Z);

                if (Math.abs(turn) < 0.15f) turn = event.getAxisValue(MotionEvent.AXIS_RX);
                if (Math.abs(turn) < 0.15f) turn = event.getAxisValue(MotionEvent.AXIS_RZ);

                if (Math.abs(x) < 0.15f && Math.abs(y) < 0.15f) {
                    x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
                    y = -event.getAxisValue(MotionEvent.AXIS_HAT_Y);
                }

                if (Math.abs(x) < 0.15f) x = 0f;
                if (Math.abs(y) < 0.15f) y = 0f;
                if (Math.abs(turn) < 0.15f) turn = 0f;

                vm.setMove(x, y);
                vm.setTurn(turn);
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_A:
                vm.setGrip(1);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_B:
                vm.setGrip(-1);
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_R2:
                vm.setLift(1);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L2:
                vm.setLift(-1);
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_START:
                vm.stop();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                vm.setMove(0f, 1f);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                vm.setMove(0f, -1f);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                vm.setMove(-1f, 0f);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                vm.setMove(1f, 0f);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_B:
                vm.setGrip(0);
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_L2:
                vm.setLift(0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                vm.setMove(0f, 0f);
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // =====================================================================
    // Model WiFi TCP Socket & ViewModel
    // =====================================================================

    public interface StatusListener {
        void onStatusChanged(String message, boolean connected);
    }

    public interface FrameListener {
        void onFrame(String frame);
    }

    public static class WifiModel {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private Socket socket;
        private OutputStream out;
        private volatile boolean connected = false;
        private StatusListener statusListener;

        public void setStatusListener(StatusListener listener) {
            this.statusListener = listener;
        }

        private void notifyStatus(String message, boolean isConnected) {
            if (statusListener != null) statusListener.onStatusChanged(message, isConnected);
        }

        public void connect(final String ip, final int port, final Network network) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    closeQuietly();
                    try {
                        socket = (network != null) ? network.getSocketFactory().createSocket() : new Socket();
                        socket.connect(new InetSocketAddress(ip, port), 4000);
                        socket.setTcpNoDelay(true);
                        out = socket.getOutputStream();
                        connected = true;
                        notifyStatus("WiFi Terhubung (" + ip + ")", true);
                    } catch (Exception e) {
                        closeQuietly();
                        notifyStatus("Gagal: " + e.getMessage(), false);
                    }
                }
            });
        }

        public void disconnect() {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    closeQuietly();
                    notifyStatus("WiFi Terputus", false);
                }
            });
        }

        public void sendCommand(final String line) {
            if (!connected) return;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (!connected || out == null) return;
                    try {
                        out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } catch (IOException e) {
                        closeQuietly();
                        notifyStatus("Koneksi WiFi Putus", false);
                    }
                }
            });
        }

        private void closeQuietly() {
            connected = false;
            try {
                if (out != null) out.close();
            } catch (IOException ignored) {
            }
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {
            }
            out = null;
            socket = null;
        }
    }

    public static class ViewModel {
        private static final long SEND_PERIOD_MS = 50;
        private static final String FRAME_STOP = "0,0,STOP,NONE,0,NONE,NONE";
        private static final float MOVE_DEADZONE = 0.15f;
        private static final float TURN_THRESHOLD = 0.30f;
        private static final String[] DIR_NAMES = {
                "RIGHT", "FORWARD-RIGHT", "FORWARD", "FORWARD-LEFT",
                "LEFT", "BACKWARD-LEFT", "BACKWARD", "BACKWARD-RIGHT"
        };

        private final WifiModel model = new WifiModel();
        private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
        private FrameListener frameListener;
        private String lastShown = "";
        private StatusListener uiListener;

        private volatile float moveX = 0f, moveY = 0f, turn = 0f;
        private volatile int grip = 0, lift = 0;

        public ViewModel() {
            model.setStatusListener(new StatusListener() {
                @Override
                public void onStatusChanged(String msg, boolean connected) {
                    if (uiListener != null) uiListener.onStatusChanged(msg, connected);
                }
            });
            startTicker();
        }

        public void setFrameListener(FrameListener listener) {
            this.frameListener = listener;
        }

        public void setStatusListener(StatusListener listener) {
            this.uiListener = listener;
        }

        public void connect(String ip, int port, Network network) {
            model.connect(ip, port, network);
        }

        public void disconnect() {
            resetState();
            model.sendCommand(FRAME_STOP);
            model.disconnect();
        }

        public void release() {
            disconnect();
            ticker.shutdown();
        }

        public void setMove(float x, float y) {
            this.moveX = x;
            this.moveY = y;
        }

        public void setTurn(float x) {
            this.turn = x;
        }

        public void setGrip(int dir) {
            this.grip = dir;
        }

        public void setLift(int dir) {
            this.lift = dir;
        }

        public void stop() {
            resetState();
            model.sendCommand(FRAME_STOP);
        }

        private void resetState() {
            moveX = 0f;
            moveY = 0f;
            turn = 0f;
            grip = 0;
            lift = 0;
        }

        private void startTicker() {
            ticker.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        String frame = buildFrame();
                        model.sendCommand(frame);
                        if (!frame.equals(lastShown)) {
                            lastShown = frame;
                            if (frameListener != null) frameListener.onFrame(frame);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }, 0, SEND_PERIOD_MS, TimeUnit.MILLISECONDS);
        }

        private String buildFrame() {
            float mx = moveX, my = moveY, tn = turn;

            int xVal = Math.round(mx * 100);
            int yVal = Math.round(my * 100);

            // Kecepatan = akar(x^2 + y^2)
            double magnitude = Math.sqrt((double) xVal * xVal + (double) yVal * yVal);
            int spd = (int) Math.min(100, Math.round(magnitude));

            float moveStrength = Math.min(1f, (float) Math.sqrt(mx * mx + my * my));
            String dir = "STOP";

            if (moveStrength >= MOVE_DEADZONE) {
                double angle = Math.toDegrees(Math.atan2(my, mx));
                if (angle < 0) angle += 360;
                dir = DIR_NAMES[((int) ((angle + 22.5) / 45.0)) % 8];
            } else {
                xVal = 0;
                yVal = 0;
                spd = 0;
            }

            String turnName = "NONE";
            if (Math.abs(tn) >= TURN_THRESHOLD) {
                turnName = tn > 0 ? "CLOCKWISE" : "ANTICLOCKWISE";
                if (spd == 0) spd = 100;
            }

            String g = grip > 0 ? "CLOSE" : (grip < 0 ? "OPEN" : "NONE");
            String l = lift > 0 ? "UP" : (lift < 0 ? "DOWN" : "NONE");

            return xVal + "," + yVal + "," + dir + "," + turnName + "," + spd + "," + g + "," + l;
        }
    }
}
