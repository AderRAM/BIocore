package com.example.terrasentry4;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private boolean bombaLigada = false;
    private TextView txtBombaStatus;
    private Button btnBomba;

    private final StringBuilder logHtml = new StringBuilder();
    private TextView txtLog;
    private ScrollView logScrollView;
    private TextView logStatusDot;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable pollingRunnable;
    private static final long POLL_INTERVAL_MS = 30_000;
    private static final int MAX_LOG_CHARS = 8000;

    private List<Float> dadosSoloReal = new ArrayList<>();
    private List<Float> dadosTempReal = new ArrayList<>();
    private List<Float> dadosArReal   = new ArrayList<>();
    private List<Float> dadosLuzReal  = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtBombaStatus = findViewById(R.id.txtBombaStatus);
        btnBomba       = findViewById(R.id.btnBomba);
        txtLog         = findViewById(R.id.txtLog);
        logScrollView  = findViewById(R.id.logScrollView);
        logStatusDot   = findViewById(R.id.logStatusDot);

        atualizarBotaoBomba();

        btnBomba.setOnClickListener(v -> toggleBomba());

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, ConfiguracoesActivity.class)));

        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            logHtml.setLength(0);
            txtLog.setText("");
        });

        configurarCard(R.id.headerSolo, R.id.contentSolo, R.id.arrowSolo,
                () -> dadosSoloReal, Color.parseColor("#4CAF50"));
        configurarCard(R.id.headerTemp, R.id.contentTemp, R.id.arrowTemp,
                () -> dadosTempReal, Color.parseColor("#FF9800"));
        configurarCard(R.id.headerAr, R.id.contentAr, R.id.arrowAr,
                () -> dadosArReal, Color.parseColor("#2196F3"));
        configurarCard(R.id.headerLuz, R.id.contentLuz, R.id.arrowLuz,
                () -> dadosLuzReal, Color.parseColor("#FFEB3B"));

        iniciarPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buscarLeituras();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pollingRunnable != null) uiHandler.removeCallbacks(pollingRunnable);
        executor.shutdown();
    }

    // --- Networking ---

    private void iniciarPolling() {
        SharedPreferences prefs = getSharedPreferences("biocore", MODE_PRIVATE);
        String servidor    = prefs.getString("servidor",      "http://100.77.100.1:5001");
        String dispositivo = prefs.getString("dispositivo_id", "esp32-biocore-01");

        appendLog("INFO", "Inicializando BioCore...");
        appendLog("INFO", "Dispositivo: " + dispositivo);
        appendLog("CONN", "Conectando a " + servidor + "...");

        pollingRunnable = new Runnable() {
            @Override public void run() {
                buscarLeituras();
                uiHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        uiHandler.postDelayed(pollingRunnable, POLL_INTERVAL_MS);
    }

    private void buscarLeituras() {
        SharedPreferences prefs = getSharedPreferences("biocore", MODE_PRIVATE);
        String servidor    = prefs.getString("servidor",      "http://100.77.100.1:5001");
        String dispositivo = prefs.getString("dispositivo_id", "esp32-biocore-01");

        executor.execute(() -> {
            try {
                URL url = new URL(servidor + "/api/leituras?dispositivo_id="
                        + dispositivo + "&limite=8");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                    }
                    conn.disconnect();
                    JSONArray arr = new JSONArray(sb.toString());
                    uiHandler.post(() -> {
                        logStatusDot.setTextColor(Color.parseColor("#4CAF50"));
                        atualizarUI(arr);
                    });
                } else {
                    conn.disconnect();
                    uiHandler.post(() -> {
                        logStatusDot.setTextColor(Color.parseColor("#E53935"));
                        appendLog("ERR", "Servidor retornou código " + code);
                    });
                }
            } catch (Exception e) {
                uiHandler.post(() -> {
                    logStatusDot.setTextColor(Color.parseColor("#E53935"));
                    appendLog("ERR", "Falha na conexão: " + e.getMessage());
                });
            }
        });
    }

    private void atualizarUI(JSONArray arr) {
        if (arr.length() == 0) {
            appendLog("WARN", "Nenhuma leitura disponível no servidor");
            return;
        }
        try {
            // arr comes in DESC order; index 0 is the most recent reading
            JSONObject latest = arr.getJSONObject(0);

            double solo = latest.optDouble("umidade_solo_percent");
            double temp = latest.optDouble("temperatura");
            double ar   = latest.optDouble("umidade_ar");
            double luz  = latest.optDouble("luminosidade_percent");
            boolean bombaServidor = latest.optInt("bomba", 0) == 1;

            if (!Double.isNaN(solo))
                ((TextView) findViewById(R.id.txtUmidadeSolo))
                        .setText(String.format(Locale.US, "%.1f%%", solo));
            if (!Double.isNaN(temp))
                ((TextView) findViewById(R.id.txtTemperatura))
                        .setText(String.format(Locale.US, "%.1f°C", temp));
            if (!Double.isNaN(ar))
                ((TextView) findViewById(R.id.txtUmidadeAr))
                        .setText(String.format(Locale.US, "%.1f%%", ar));
            if (!Double.isNaN(luz))
                ((TextView) findViewById(R.id.txtLuminosidade))
                        .setText(String.format(Locale.US, "%.1f%%", luz));

            // Reverse to chronological order for charts
            List<Float> soloList = new ArrayList<>(), tempList = new ArrayList<>(),
                        arList   = new ArrayList<>(), luzList  = new ArrayList<>();
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject r = arr.getJSONObject(i);
                soloList.add((float) r.optDouble("umidade_solo_percent", 0));
                tempList.add((float) r.optDouble("temperatura",          0));
                arList.add((float)   r.optDouble("umidade_ar",           0));
                luzList.add((float)  r.optDouble("luminosidade_percent", 0));
            }
            dadosSoloReal = soloList;
            dadosTempReal = tempList;
            dadosArReal   = arList;
            dadosLuzReal  = luzList;

            appendLog("CONN", "Dados recebidos");
            appendLog("DATA", String.format(Locale.US,
                    "Solo %.1f%% · Temp %.1f°C · Ar %.1f%% · Luz %.1f%%",
                    solo, temp, ar, luz));

            if (bombaServidor != bombaLigada) {
                bombaLigada = bombaServidor;
                atualizarBotaoBomba();
            }

        } catch (Exception e) {
            appendLog("ERR", "Erro ao processar dados: " + e.getMessage());
        }
    }

    private void enviarComandoBomba(boolean ligar) {
        SharedPreferences prefs = getSharedPreferences("biocore", MODE_PRIVATE);
        String servidor    = prefs.getString("servidor",      "http://100.77.100.1:5001");
        String dispositivo = prefs.getString("dispositivo_id", "esp32-biocore-01");

        executor.execute(() -> {
            try {
                URL url = new URL(servidor + "/api/comando");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String body = "{\"dispositivo_id\":\"" + dispositivo
                        + "\",\"bomba\":" + ligar + "}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                conn.disconnect();
                boolean ok = (code == 201);
                uiHandler.post(() -> {
                    if (ok) {
                        appendLog("PUMP", "Comando enviado: bomba "
                                + (ligar ? "LIGADA" : "DESLIGADA"));
                    } else {
                        appendLog("ERR", "Falha ao enviar comando (código " + code + ")");
                    }
                });
            } catch (Exception e) {
                uiHandler.post(() ->
                        appendLog("ERR", "Erro ao enviar comando: " + e.getMessage()));
            }
        });
    }

    // --- UI helpers ---

    private void toggleBomba() {
        bombaLigada = !bombaLigada;
        atualizarBotaoBomba();
        enviarComandoBomba(bombaLigada);
    }

    private void atualizarBotaoBomba() {
        txtBombaStatus.setText(bombaLigada ? "LIGADA" : "DESLIGADA");
        txtBombaStatus.setTextColor(
                Color.parseColor(bombaLigada ? "#4CAF50" : "#E53935"));
        btnBomba.setText(bombaLigada ? "Desligar Bomba" : "Ligar Bomba");
        btnBomba.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.parseColor(bombaLigada ? "#4CAF50" : "#E53935")));
    }

    private void appendLog(String tag, String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String tagColor;
        switch (tag) {
            case "CONN": tagColor = "#4CAF50"; break;
            case "DATA": tagColor = "#2196F3"; break;
            case "PUMP": tagColor = "#FF9800"; break;
            case "WARN": tagColor = "#FFEB3B"; break;
            case "ERR":  tagColor = "#E53935"; break;
            default:     tagColor = "#888888"; break;
        }

        if (logHtml.length() > 0) logHtml.append("<br>");
        logHtml.append(String.format(
                "<font color='#444444'>[%s]</font> <font color='%s'>[%s]</font>"
                + " <font color='#CCCCCC'>%s</font>",
                time, tagColor, tag, msg));

        if (logHtml.length() > MAX_LOG_CHARS) {
            int cutAt = logHtml.indexOf("<br>", logHtml.length() - MAX_LOG_CHARS);
            if (cutAt > 0) logHtml.delete(0, cutAt + 4);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            txtLog.setText(Html.fromHtml(logHtml.toString(), Html.FROM_HTML_MODE_COMPACT));
        } else {
            txtLog.setText(Html.fromHtml(logHtml.toString()));
        }

        logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void configurarCard(int headerId, int contentId, int arrowId,
                                 ChartDataProvider provider, int color) {
        View header             = findViewById(headerId);
        final LinearLayout content = findViewById(contentId);
        final TextView arrow    = findViewById(arrowId);

        header.setOnClickListener(v -> {
            boolean isExpanded = content.getVisibility() == View.VISIBLE;
            if (isExpanded) {
                content.setVisibility(View.GONE);
                arrow.setText(" ▶");
            } else {
                content.removeAllViews();
                LineChartView chart = new LineChartView(this);
                chart.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
                chart.setValores(provider.getDados(), color);
                content.addView(chart);
                content.setVisibility(View.VISIBLE);
                arrow.setText(" ▼");
            }
        });

        arrow.setText(" ▶");
        content.setVisibility(View.GONE);
    }

    private interface ChartDataProvider {
        List<Float> getDados();
    }
}
