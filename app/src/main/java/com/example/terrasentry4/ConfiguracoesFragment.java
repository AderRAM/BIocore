package com.example.terrasentry4;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

import static android.content.Context.MODE_PRIVATE;

public class ConfiguracoesFragment extends Fragment {

    private TextInputEditText etServidor;
    private TextInputEditText etDispositivo;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_configuracoes, container, false);

        etServidor    = view.findViewById(R.id.etServidor);
        etDispositivo = view.findViewById(R.id.etDispositivo);

        SharedPreferences prefs = requireContext()
                .getSharedPreferences("biocore", MODE_PRIVATE);
        etServidor.setText(prefs.getString("servidor", "http://100.77.100.1:5001"));
        etDispositivo.setText(prefs.getString("dispositivo_id", "esp32-biocore-01"));

        view.findViewById(R.id.btnBack).setOnClickListener(v -> {
            salvar();
            requireActivity().finish();
        });

        return view;
    }

    private void salvar() {
        String servidor    = etServidor.getText()    != null
                ? etServidor.getText().toString().trim()    : "";
        String dispositivo = etDispositivo.getText() != null
                ? etDispositivo.getText().toString().trim() : "";

        requireContext().getSharedPreferences("biocore", MODE_PRIVATE)
                .edit()
                .putString("servidor",      servidor.isEmpty()    ? "http://100.77.100.1:5001" : servidor)
                .putString("dispositivo_id", dispositivo.isEmpty() ? "esp32-biocore-01"         : dispositivo)
                .apply();
    }
}
