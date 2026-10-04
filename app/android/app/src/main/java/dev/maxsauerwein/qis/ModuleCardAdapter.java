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

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.maxsauerwein.qis.model.AttemptRow;
import dev.maxsauerwein.qis.model.ModuleCardData;
import dev.maxsauerwein.qis.util.GradeStyling;

/** Rendert die Notenliste als flache Zeilenfolge mit mehreren Viewtypen: Abschnitts-Header
 *  (Kernmodule/Pflichtmodule/Wahlpflichtmodule oder Semester-Trennzeile), Modul-Karten und die
 *  klappbare "Archiviert"-Zeile. Port der SwiftUI-List-mit-Sections aus GradesView.swift auf ein
 *  Mehrfach-Viewtyp-RecyclerView. Die Zeilenliste wird von MainActivity gebaut und per
 *  {@link #submitRows} hereingereicht; die Adapter-Instanz bleibt über Filter-/Sortier-/Archiv-
 *  Änderungen hinweg bestehen, damit der manuelle Auf-/Zu-Klappzustand einer Karte nicht bei jeder
 *  Neuberechnung verloren geht. */
public final class ModuleCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public enum RowType { SECTION_HEADER, SEMESTER_DIVIDER, MODULE_CARD, ARCHIVE_TOGGLE }

    public static final class Row {
        public final RowType type;
        public final String title;
        public final ModuleCardData card;
        public final boolean isArchivedCard;
        public final int archiveCount;
        public final boolean archiveExpanded;

        private Row(RowType type, String title, ModuleCardData card, boolean isArchivedCard,
                    int archiveCount, boolean archiveExpanded) {
            this.type = type;
            this.title = title;
            this.card = card;
            this.isArchivedCard = isArchivedCard;
            this.archiveCount = archiveCount;
            this.archiveExpanded = archiveExpanded;
        }

        public static Row sectionHeader(String title) {
            return new Row(RowType.SECTION_HEADER, title, null, false, 0, false);
        }

        public static Row semesterDivider(String title) {
            return new Row(RowType.SEMESTER_DIVIDER, title, null, false, 0, false);
        }

        public static Row moduleCard(ModuleCardData card, boolean isArchivedCard) {
            return new Row(RowType.MODULE_CARD, null, card, isArchivedCard, 0, false);
        }

        public static Row archiveToggle(int count, boolean expanded) {
            return new Row(RowType.ARCHIVE_TOGGLE, null, null, false, count, expanded);
        }
    }

    public interface Listener {
        void onToggleAverageInclusion(ModuleCardData card, boolean includedInAverage);
        void onArchive(ModuleCardData card);
        void onRestore(ModuleCardData card);
        void onToggleArchiveSection();
    }

    private final Listener listener;
    private final Set<String> expandedCardIds = new HashSet<>();
    private final Set<String> seenCardIds = new HashSet<>();

    private List<Row> rows = new ArrayList<>();
    private Set<String> visibleFields = new HashSet<>();
    private Map<String, String> customColors = new java.util.HashMap<>();
    private Set<String> excludedFromAverageModuleKeys = new HashSet<>();

    public ModuleCardAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitRows(List<Row> newRows, Set<String> visibleFields, Map<String, String> customColors,
                            Set<String> excludedFromAverageModuleKeys) {
        this.rows = newRows;
        this.visibleFields = visibleFields;
        this.customColors = customColors;
        this.excludedFromAverageModuleKeys = excludedFromAverageModuleKeys;
        for (Row row : newRows) {
            if (row.type == RowType.MODULE_CARD) {
                String id = row.card.id();
                if (seenCardIds.add(id) && row.card.isNew) {
                    expandedCardIds.add(id);
                }
            }
        }
        notifyDataSetChanged();
    }

    public Row rowAt(int position) {
        return rows.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        switch (rows.get(position).type) {
            case SECTION_HEADER: return 0;
            case SEMESTER_DIVIDER: return 1;
            case ARCHIVE_TOGGLE: return 3;
            case MODULE_CARD:
            default: return 2;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case 0:
                return new SectionHeaderHolder(inflater.inflate(R.layout.item_list_section_header, parent, false));
            case 1:
                return new SemesterDividerHolder(inflater.inflate(R.layout.item_semester_divider, parent, false));
            case 3:
                return new ArchiveToggleHolder(inflater.inflate(R.layout.item_archive_toggle, parent, false));
            case 2:
            default:
                return new CardHolder(inflater.inflate(R.layout.item_module_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof SectionHeaderHolder) {
            ((SectionHeaderHolder) holder).title.setText(row.title);
        } else if (holder instanceof SemesterDividerHolder) {
            ((SemesterDividerHolder) holder).title.setText(row.title);
        } else if (holder instanceof ArchiveToggleHolder) {
            bindArchiveToggle((ArchiveToggleHolder) holder, row);
        } else if (holder instanceof CardHolder) {
            bindCard((CardHolder) holder, row);
        }
    }

    private void bindArchiveToggle(ArchiveToggleHolder holder, Row row) {
        holder.count.setText(String.valueOf(row.archiveCount));
        holder.expandIcon.setRotation(row.archiveExpanded ? 180f : 0f);
        holder.itemView.setOnClickListener(v -> listener.onToggleArchiveSection());
    }

    private void bindCard(CardHolder holder, Row row) {
        ModuleCardData card = row.card;
        boolean isExpanded = expandedCardIds.contains(card.id());

        holder.moduleName.setText(card.moduleName);
        holder.moduleGrade.setText(card.grade.isEmpty() ? "–" : card.grade);

        holder.newBadge.setVisibility(card.isNew ? View.VISIBLE : View.GONE);
        if (card.isNew) {
            int accent = holder.itemView.getResources().getColor(R.color.qis_primary, null);
            holder.newBadge.setTextColor(accent);
            holder.newBadge.setBackground(pillDrawable(GradeStyling.withAlpha(accent, 36)));
        }

        GradeStyling.StatusCategory category = card.statusCategory();
        if (category != null) {
            holder.statusChip.setVisibility(View.VISIBLE);
            holder.statusChip.setText(card.statusLabel());
            holder.statusChip.setTextColor(GradeStyling.accent(category, customColors));
            holder.statusChip.setBackground(pillDrawable(GradeStyling.backgroundTint(category, customColors)));
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
        holder.averageDivider.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.averageSwitch.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        androidx.core.view.ViewCompat.setStateDescription(holder.cardHeader,
                isExpanded ? "Aufgeklappt" : "Zugeklappt");

        if (isExpanded) {
            renderAttempts(holder, card.attempts);
            holder.averageSwitch.setOnCheckedChangeListener(null);
            holder.averageSwitch.setChecked(!excludedFromAverageModuleKeys.contains(card.id()));
            holder.averageSwitch.setOnCheckedChangeListener((button, checked) ->
                    listener.onToggleAverageInclusion(card, checked));
        }

        holder.cardHeader.setOnClickListener(v -> {
            if (expandedCardIds.contains(card.id())) {
                expandedCardIds.remove(card.id());
            } else {
                expandedCardIds.add(card.id());
            }
            notifyItemChanged(holder.getBindingAdapterPosition());
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

    private void renderAttempts(CardHolder holder, List<AttemptRow> attempts) {
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
        return rows.size();
    }

    static final class SectionHeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;

        SectionHeaderHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.sectionHeaderTitle);
        }
    }

    static final class SemesterDividerHolder extends RecyclerView.ViewHolder {
        final TextView title;

        SemesterDividerHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.semesterDividerTitle);
        }
    }

    static final class ArchiveToggleHolder extends RecyclerView.ViewHolder {
        final TextView count;
        final ImageView expandIcon;

        ArchiveToggleHolder(@NonNull View itemView) {
            super(itemView);
            count = itemView.findViewById(R.id.archiveToggleCount);
            expandIcon = itemView.findViewById(R.id.archiveToggleExpandIcon);
        }
    }

    static final class CardHolder extends RecyclerView.ViewHolder {
        final LinearLayout cardHeader;
        final TextView moduleName;
        final TextView moduleGrade;
        final TextView newBadge;
        final TextView statusChip;
        final TextView ectsChip;
        final ImageView expandIcon;
        final View attemptsDivider;
        final LinearLayout attemptsContainer;
        final View averageDivider;
        final MaterialSwitch averageSwitch;

        CardHolder(@NonNull View itemView) {
            super(itemView);
            cardHeader = itemView.findViewById(R.id.cardHeader);
            moduleName = itemView.findViewById(R.id.moduleName);
            moduleGrade = itemView.findViewById(R.id.moduleGrade);
            newBadge = itemView.findViewById(R.id.newBadge);
            statusChip = itemView.findViewById(R.id.statusChip);
            ectsChip = itemView.findViewById(R.id.ectsChip);
            expandIcon = itemView.findViewById(R.id.expandIcon);
            attemptsDivider = itemView.findViewById(R.id.attemptsDivider);
            attemptsContainer = itemView.findViewById(R.id.attemptsContainer);
            averageDivider = itemView.findViewById(R.id.averageDivider);
            averageSwitch = itemView.findViewById(R.id.averageSwitch);
        }
    }
}
