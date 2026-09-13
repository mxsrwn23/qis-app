package dev.maxsauerwein.qis;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import dev.maxsauerwein.qis.storage.GradeSettingsStore;
import dev.maxsauerwein.qis.util.GradeStyling;

public final class DisplayOptionsActivity extends AppCompatActivity {

    public static Intent createIntent(Context context) {
        return new Intent(context, DisplayOptionsActivity.class);
    }

    /** Kuratierte Farbmuster, da Android keine eingebaute interaktive Farbauswahl bietet. */
    private static final int[] PRESET_COLORS = {
            0xFFB24A43, 0xFFC77A3F, 0xFFB79A3B, 0xFF3F8F5F, 0xFF3F8F8A, 0xFF4F7A96,
            0xFF5B6FA6, 0xFF7C6BB0, 0xFF96793A, 0xFF8E8E93, 0xFF6B6B6B, 0xFF4A4A4A,
    };

    private GradeSettingsStore settingsStore;
    private GradeSettingsStore.Settings settings;
    private LinearLayout colorsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_options);

        MaterialToolbar toolbar = findViewById(R.id.displayOptionsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        settingsStore = new GradeSettingsStore(getApplicationContext());
        settings = settingsStore.load();

        RadioGroup averageModeGroup = findViewById(R.id.averageModeGroup);
        averageModeGroup.check(averageModeToId(settings.averageMode));
        averageModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            settings.averageMode = idToAverageMode(checkedId);
            settingsStore.save(settings);
        });

        MaterialSwitch hideStudienleistungSwitch = findViewById(R.id.hideStudienleistungSwitch);
        hideStudienleistungSwitch.setChecked(settings.hideStudienleistungen);
        hideStudienleistungSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.hideStudienleistungen = isChecked;
            settingsStore.save(settings);
        });

        bindFieldSwitch(R.id.fieldSemesterSwitch, "semester");
        bindFieldSwitch(R.id.fieldNoteSwitch, "note");
        bindFieldSwitch(R.id.fieldVersuchSwitch, "versuch");
        bindFieldSwitch(R.id.fieldDatumSwitch, "datum");

        colorsContainer = findViewById(R.id.colorsContainer);
        renderColorRows();

        findViewById(R.id.resetColorsButton).setOnClickListener(v -> {
            settings.customColors.clear();
            settingsStore.save(settings);
            renderColorRows();
        });
    }

    private void bindFieldSwitch(int viewId, String field) {
        MaterialSwitch toggle = findViewById(viewId);
        toggle.setChecked(settings.visibleAttemptFields.contains(field));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                settings.visibleAttemptFields.add(field);
            } else {
                settings.visibleAttemptFields.remove(field);
            }
            settingsStore.save(settings);
        });
    }

    private void renderColorRows() {
        colorsContainer.removeAllViews();
        for (GradeStyling.ColorKey key : GradeStyling.ColorKey.values()) {
            TextView label = new TextView(this);
            label.setText(key.label);
            label.setPadding(0, 16, 0, 4);
            colorsContainer.addView(label);
            colorsContainer.addView(buildSwatchGrid(key));
        }
    }

    private GridLayout buildSwatchGrid(GradeStyling.ColorKey key) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        int currentColor = GradeStyling.accent(key, settings.customColors);
        int swatchSize = dp(32);
        int margin = dp(4);

        for (int color : PRESET_COLORS) {
            View swatch = new View(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = swatchSize;
            params.height = swatchSize;
            params.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(params);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            boolean isSelected = (color | 0xFF000000) == currentColor;
            if (isSelected) {
                shape.setStroke(dp(3), 0xFF000000);
            }
            swatch.setBackground(shape);

            swatch.setOnClickListener(v -> {
                settings.customColors.put(key.key, GradeStyling.toHexString(color));
                settingsStore.save(settings);
                renderColorRows();
            });

            grid.addView(swatch);
        }
        return grid;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int averageModeToId(GradeSettingsStore.AverageMode mode) {
        switch (mode) {
            case LAST: return R.id.averageModeLast;
            case BEST: return R.id.averageModeBest;
            case ALL:
            default: return R.id.averageModeAll;
        }
    }

    private GradeSettingsStore.AverageMode idToAverageMode(int id) {
        if (id == R.id.averageModeLast) {
            return GradeSettingsStore.AverageMode.LAST;
        } else if (id == R.id.averageModeBest) {
            return GradeSettingsStore.AverageMode.BEST;
        }
        return GradeSettingsStore.AverageMode.ALL;
    }
}
