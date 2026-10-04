package dev.maxsauerwein.qis;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

import dev.maxsauerwein.qis.storage.GradeSettingsStore;
import dev.maxsauerwein.qis.util.QISLabels;

/** Einmaliger Einrichtungsschritt, wenn QIS beim Login mehrere Abschluss-Typen und/oder
 *  Studiengänge für den Nutzer meldet: zeigt ausschließlich die tatsächlich in
 *  {@code settings.availableDegreeOptions} gefundenen Optionen zur Auswahl an -- nie eine
 *  hartkodierte Liste. MainActivity startet diese Activity, solange
 *  {@code GradeSettingsStore.Settings.needsDegreeSetup()} zutrifft, und erwartet
 *  {@code RESULT_OK}, sobald der Nutzer eine vollständige Wahl getroffen hat. Port von
 *  DegreeSetupView.swift. */
public final class DegreeSetupActivity extends AppCompatActivity {

    public static Intent createIntent(Context context) {
        return new Intent(context, DegreeSetupActivity.class);
    }

    private GradeSettingsStore settingsStore;
    private GradeSettingsStore.Settings settings;

    private LinearLayout abschlussSection;
    private LinearLayout abschlussContainer;
    private LinearLayout fachSection;
    private LinearLayout fachContainer;
    private MaterialButton confirmButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_degree_setup);

        settingsStore = new GradeSettingsStore(getApplicationContext());
        settings = settingsStore.load();

        abschlussSection = findViewById(R.id.abschlussSection);
        abschlussContainer = findViewById(R.id.abschlussContainer);
        fachSection = findViewById(R.id.fachSection);
        fachContainer = findViewById(R.id.fachContainer);
        confirmButton = findViewById(R.id.degreeSetupConfirmButton);
        confirmButton.setOnClickListener(v -> confirm());

        // Einmaliger, nicht überspringbarer Einrichtungsschritt -- es gibt (wie im iOS-Pendant,
        // das die Navigationsleiste hier ausblendet) bewusst keinen Weg zurück ohne vollständige
        // Auswahl.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // no-op
            }
        });

        render();
    }

    private void render() {
        List<String> abschlussOptions = settings.distinctAbschlussOptions();
        abschlussContainer.removeAllViews();
        if (abschlussOptions.size() > 1) {
            abschlussSection.setVisibility(View.VISIBLE);
            for (String abschluss : abschlussOptions) {
                abschlussContainer.addView(optionCard(
                        QISLabels.degreeFullName(abschluss),
                        abschluss.equals(settings.selectedAbschluss),
                        () -> selectAbschluss(abschluss)));
            }
        } else if (!settings.selectedAbschluss.isEmpty()) {
            abschlussSection.setVisibility(View.VISIBLE);
            abschlussContainer.addView(confirmedCard(QISLabels.degreeFullName(settings.selectedAbschluss)));
        } else {
            abschlussSection.setVisibility(View.GONE);
        }

        List<String> fachOptions = settings.fachOptions(settings.selectedAbschluss);
        fachContainer.removeAllViews();
        if (settings.selectedAbschluss.isEmpty()) {
            fachSection.setVisibility(View.GONE);
        } else if (fachOptions.size() > 1) {
            fachSection.setVisibility(View.VISIBLE);
            for (String fach : fachOptions) {
                fachContainer.addView(optionCard(
                        QISLabels.fachName(fach),
                        fach.equals(settings.selectedFach),
                        () -> selectFach(fach)));
            }
        } else if (!settings.selectedFach.isEmpty()) {
            fachSection.setVisibility(View.VISIBLE);
            fachContainer.addView(confirmedCard(QISLabels.fachName(settings.selectedFach)));
        } else {
            fachSection.setVisibility(View.GONE);
        }

        confirmButton.setEnabled(!settings.selectedAbschluss.isEmpty() && !settings.selectedFach.isEmpty());
    }

    private void selectAbschluss(String abschluss) {
        settings.selectedAbschluss = abschluss;
        List<String> fachOptions = settings.fachOptions(abschluss);
        settings.selectedFach = fachOptions.size() == 1 ? fachOptions.get(0) : "";
        render();
    }

    private void selectFach(String fach) {
        settings.selectedFach = fach;
        render();
    }

    private View optionCard(String label, boolean isSelected, Runnable onSelect) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_degree_option, abschlussContainer, false);
        TextView labelView = card.findViewById(R.id.optionLabel);
        TextView checkView = card.findViewById(R.id.optionCheck);
        MaterialCardView cardView = (MaterialCardView) card;
        labelView.setText(label);
        checkView.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        cardView.setChecked(isSelected);
        int accent = getResources().getColor(R.color.qis_primary, null);
        cardView.setStrokeColor(isSelected ? accent : themeColor(com.google.android.material.R.attr.colorOutlineVariant));
        card.setOnClickListener(v -> onSelect.run());
        return card;
    }

    private View confirmedCard(String value) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_degree_option, abschlussContainer, false);
        TextView labelView = card.findViewById(R.id.optionLabel);
        TextView checkView = card.findViewById(R.id.optionCheck);
        labelView.setText(value);
        checkView.setVisibility(View.VISIBLE);
        card.setClickable(false);
        return card;
    }

    private int themeColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    private void confirm() {
        settingsStore.save(settings);
        setResult(Activity.RESULT_OK);
        finish();
    }
}
