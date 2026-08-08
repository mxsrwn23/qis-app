package dev.maxsauerwein.qis;

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.maxsauerwein.qis.model.AttemptRow;
import dev.maxsauerwein.qis.model.ModuleCardData;
import dev.maxsauerwein.qis.util.GradeStyling;

public final class ModuleCardAdapter extends RecyclerView.Adapter<ModuleCardAdapter.ViewHolder> {

    private final List<ModuleCardData> cards;
    private final Set<String> visibleFields;
    private final java.util.Map<String, String> customColors;
    private final Set<Integer> expandedPositions = new HashSet<>();

    public ModuleCardAdapter(List<ModuleCardData> cards, Set<String> visibleFields,
                              java.util.Map<String, String> customColors) {
        this.cards = cards;
        this.visibleFields = visibleFields;
        this.customColors = customColors;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_module_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModuleCardData card = cards.get(position);
        boolean isExpanded = expandedPositions.contains(position);

        holder.moduleName.setText(card.moduleName);
        holder.moduleGrade.setText(card.grade.isEmpty() ? "–" : card.grade);

        GradeStyling.StatusCategory category = card.statusCategory();
        if (category != null) {
            holder.statusChip.setVisibility(View.VISIBLE);
            holder.statusChip.setText(card.statusLabel());
            holder.statusChip.setTextColor(GradeStyling.accent(category.colorKey, customColors));
            holder.statusChip.setBackground(
                    pillDrawable(GradeStyling.backgroundTint(category.colorKey, customColors)));
        } else {
            holder.statusChip.setVisibility(View.GONE);
        }

        if (card.ects.isEmpty()) {
            holder.ectsChip.setVisibility(View.GONE);
        } else {
            holder.ectsChip.setVisibility(View.VISIBLE);
            holder.ectsChip.setText(card.ects + " ECTS");
            int surfaceVariant = themeColor(holder.itemView.getContext(), com.google.android.material.R.attr.colorSurfaceVariant);
            holder.ectsChip.setBackground(pillDrawable(surfaceVariant));
        }

        holder.expandIcon.setRotation(isExpanded ? 180f : 0f);
        holder.attemptsDivider.setVisibility(isExpanded && !card.attempts.isEmpty() ? View.VISIBLE : View.GONE);
        holder.attemptsContainer.setVisibility(isExpanded && !card.attempts.isEmpty() ? View.VISIBLE : View.GONE);
        androidx.core.view.ViewCompat.setStateDescription(holder.cardHeader,
                isExpanded ? "Aufgeklappt" : "Zugeklappt");

        if (isExpanded) {
            renderAttempts(holder, card.attempts);
        }

        holder.cardHeader.setOnClickListener(v -> {
            if (expandedPositions.contains(position)) {
                expandedPositions.remove(position);
            } else {
                expandedPositions.add(position);
            }
            notifyItemChanged(position);
        });
    }

    private int themeColor(android.content.Context context, int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    private GradientDrawable pillDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(100f);
        drawable.setColor(color);
        return drawable;
    }

    private void renderAttempts(ViewHolder holder, List<AttemptRow> attempts) {
        holder.attemptsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(holder.attemptsContainer.getContext());
        for (AttemptRow attempt : attempts) {
            View row = inflater.inflate(R.layout.item_attempt_row, holder.attemptsContainer, false);
            TextView semester = row.findViewById(R.id.attemptSemester);
            TextView note = row.findViewById(R.id.attemptNote);
            TextView versuch = row.findViewById(R.id.attemptVersuch);
            TextView datum = row.findViewById(R.id.attemptDatum);

            semester.setText(visibleFields.contains("semester") ? attempt.semester : "");
            note.setText(visibleFields.contains("note") && !attempt.note.isEmpty() ? attempt.note : "–");
            versuch.setText(visibleFields.contains("versuch") && !attempt.versuch.isEmpty()
                    ? "Versuch " + attempt.versuch : "");
            datum.setText(visibleFields.contains("datum") ? attempt.datum : "");

            if (attempt.isFailed()) {
                note.setPaintFlags(note.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                row.setAlpha(0.6f);
            }

            holder.attemptsContainer.addView(row);
        }
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout cardHeader;
        final TextView moduleName;
        final TextView moduleGrade;
        final TextView statusChip;
        final TextView ectsChip;
        final ImageView expandIcon;
        final View attemptsDivider;
        final LinearLayout attemptsContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardHeader = itemView.findViewById(R.id.cardHeader);
            moduleName = itemView.findViewById(R.id.moduleName);
            moduleGrade = itemView.findViewById(R.id.moduleGrade);
            statusChip = itemView.findViewById(R.id.statusChip);
            ectsChip = itemView.findViewById(R.id.ectsChip);
            expandIcon = itemView.findViewById(R.id.expandIcon);
            attemptsDivider = itemView.findViewById(R.id.attemptsDivider);
            attemptsContainer = itemView.findViewById(R.id.attemptsContainer);
        }
    }
}
