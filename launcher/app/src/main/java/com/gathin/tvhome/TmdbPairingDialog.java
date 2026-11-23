package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class TmdbPairingDialog {
    private final AlertDialog dialog;
    private final TmdbPairingServer server;

    TmdbPairingDialog(UnifiedCatalogSettingsActivity activity, Runnable onSaved, Runnable onDismiss) throws IOException {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 24), dp(activity, 16), dp(activity, 24), dp(activity, 12));

        LinearLayout details = StreamUi.column(activity);
        details.setPadding(dp(activity, 20), 0, 0, 0);
        details.addView(StreamUi.text(activity, "Escanea el QR con la cámara del móvil",
                20, StreamUi.WHITE, true));
        TextView instructions = StreamUi.text(activity,
                "Conecta el móvil a la misma red que la TV, pega tu clave API v3 y pulsa Guardar en la TV.",
                16, StreamUi.DIM, false);
        instructions.setPadding(0, dp(activity, 10), 0, dp(activity, 16));
        details.addView(instructions);
        TextView status = StreamUi.text(activity, "", 16, StreamUi.readableAccent(activity), true);
        details.addView(status);
        TextView note = StreamUi.text(activity, "Usa una red de confianza. La clave viaja por HTTP local.",
                13, StreamUi.DIM, false);
        note.setPadding(0, dp(activity, 10), 0, 0);
        details.addView(note);

        dialog = StreamUi.dialog(activity).setTitle("Configurar TMDB desde el móvil")
                .setView(row).setNegativeButton("Cerrar", null).create();
        server = new TmdbPairingServer(lanAddress(activity), template(activity), new TmdbPairingServer.Callback() {
            @Override public boolean validate(String key) throws IOException {
                return UnifiedCatalogApi.validatesPairingKey(key);
            }

            @Override public boolean save(String key) {
                return UnifiedCatalogPrefs.prefs(activity).edit().putString(UnifiedCatalogPrefs.KEY, key).commit();
            }

            @Override public void finished(boolean saved) {
                activity.runOnUiThread(() -> {
                    if (!dialog.isShowing()) return;
                    if (saved) {
                        dialog.dismiss();
                        onSaved.run();
                    }
                });
            }
        });

        View qr = new QrTile(activity, server.url());
        int tileSize = dp(activity, 172);
        row.addView(qr, new LinearLayout.LayoutParams(tileSize, tileSize));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        CountDownTimer timer = new CountDownTimer(TmdbPairingServer.LIFETIME_MS, 1000) {
            @Override public void onTick(long remaining) {
                long seconds = (remaining + 999) / 1000;
                status.setText(String.format(Locale.ROOT, "Disponible durante %d:%02d", seconds / 60, seconds % 60));
            }

            @Override public void onFinish() {
                server.close();
                qr.setVisibility(View.INVISIBLE);
                instructions.setText("Cierra esta pantalla y vuelve a abrir la opción para generar un nuevo QR.");
                status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
                status.setText("El QR ha caducado");
            }
        };
        dialog.setOnDismissListener(ignored -> {
            timer.cancel();
            server.close();
            onDismiss.run();
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setBackgroundDrawable(StreamUi.panel(activity));
            TextView close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            StreamUi.background(close, false, false);
            close.setTextColor(StreamUi.WHITE);
            close.setAllCaps(false);
        });
        dialog.show();
        timer.start();
        server.start();
    }

    void dismiss() {
        server.close();
        dialog.dismiss();
    }

    private static Inet4Address lanAddress(Context context) throws IOException {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        Network active = manager.getActiveNetwork();
        Inet4Address address = addressOn(manager, active);
        if (address != null) return address;
        for (Network network : manager.getAllNetworks()) {
            address = addressOn(manager, network);
            if (address != null) return address;
        }
        throw new IOException("No local IPv4 address");
    }

    private static Inet4Address addressOn(ConnectivityManager manager, Network network) {
        if (network == null) return null;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        LinkProperties properties = manager.getLinkProperties(network);
        if (capabilities == null || properties == null || !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) return null;
        for (LinkAddress link : properties.getLinkAddresses()) {
            InetAddress address = link.getAddress();
            if (address instanceof Inet4Address && address.isSiteLocalAddress()) return (Inet4Address) address;
        }
        return null;
    }

    private static String template(Context context) throws IOException {
        try (InputStream input = context.getAssets().open("tmdb-pairing.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static int dp(Context context, int n) { return StreamUi.dp(context, n); }

    private static final class QrTile extends View {
        private final boolean[][] modules;
        private final Paint paint = new Paint();

        QrTile(Context context, String url) {
            super(context);
            modules = Qr.encode(url);
            setContentDescription("Código QR de emparejamiento de UnifiedTV");
            setBackground(StreamUi.shape(context, Color.WHITE, 12, Color.TRANSPARENT));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int size = modules.length;
            int scale = Math.min(getWidth(), getHeight()) / (size + 8);
            int left = (getWidth() - size * scale) / 2;
            int top = (getHeight() - size * scale) / 2;
            paint.setColor(Color.BLACK);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (!modules[y][x]) continue;
                    int px = left + x * scale;
                    int py = top + y * scale;
                    canvas.drawRect(px, py, px + scale, py + scale, paint);
                }
            }
        }
    }
}
