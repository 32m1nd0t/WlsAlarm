package com.example.wlsreminderapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wlsreminderapp.databinding.ItemReminderBinding;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class ReminderAdapter extends ListAdapter<Reminder, ReminderAdapter.VH> {

    public interface OnDeleteListener { void onDelete(Reminder r); }
    public interface OnToggleListener { void onToggle(Reminder r, boolean enabled); }
    public interface OnEditListener   { void onEdit(Reminder r); }

    private final OnDeleteListener onDelete;
    private final OnToggleListener onToggle;
    private final OnEditListener   onEdit;

    public ReminderAdapter(OnDeleteListener onDelete,
                           OnToggleListener onToggle,
                           OnEditListener onEdit) {
        super(DIFF);
        this.onDelete = onDelete;
        this.onToggle = onToggle;
        this.onEdit   = onEdit;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemReminderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Reminder r = getItem(position);
        holder.b.tvName.setText(r.name);
        holder.b.tvDesc.setText(r.description);
        holder.b.tvTime.setText(
                r.times == null ? "" : r.times.replace(",", "  /  "));
        holder.b.tvDays.setText(getDaysDisplay(r.days));

        // 오늘 완료 여부 표시
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        boolean doneToday = today.equals(r.lastCompletedDate);
        holder.b.tvCompleted.setVisibility(doneToday ? View.VISIBLE : View.GONE);

        holder.b.switchEnabled.setOnCheckedChangeListener(null);
        holder.b.switchEnabled.setChecked(r.isEnabled);
        holder.b.switchEnabled.setOnCheckedChangeListener(
                (btn, checked) -> onToggle.onToggle(r, checked));
        holder.b.btnEdit.setOnClickListener(v -> onEdit.onEdit(r));
        holder.b.btnDelete.setOnClickListener(v -> onDelete.onDelete(r));
    }

    // 요일 표시 텍스트 생성
    private String getDaysDisplay(String days) {
        Set<Integer> sel = AlarmScheduler.parseDays(days);
        if (sel.size() == 7) return "매일";
        if (sel.containsAll(Arrays.asList(2,3,4,5,6)) && sel.size() == 5) return "평일";
        if (sel.containsAll(Arrays.asList(1,7)) && sel.size() == 2) return "주말";
        String[] names = {"일","월","화","수","목","금","토"};
        int[] order = {2,3,4,5,6,7,1};
        StringBuilder sb = new StringBuilder();
        for (int d : order) {
            if (sel.contains(d)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(names[d - 1]);
            }
        }
        return sb.toString();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemReminderBinding b;
        VH(ItemReminderBinding b) { super(b.getRoot()); this.b = b; }
    }

    static final DiffUtil.ItemCallback<Reminder> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Reminder a, @NonNull Reminder b) {
            return a.id == b.id;
        }
        @Override
        public boolean areContentsTheSame(@NonNull Reminder a, @NonNull Reminder b) {
            return a.id == b.id && a.name.equals(b.name)
                    && a.isEnabled == b.isEnabled
                    && a.times.equals(b.times)
                    && (a.days == null ? b.days == null : a.days.equals(b.days))
                    && (a.lastCompletedDate == null ? b.lastCompletedDate == null
                            : a.lastCompletedDate.equals(b.lastCompletedDate));
        }
    };
}