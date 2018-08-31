package com.altertech.scanner.ui.log;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.altertech.scanner.R;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by oshevchuk on 02.08.2018
 */
public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private Context context;
    private List<String> items;

    public LogAdapter(Context context, List<String> items) {
        this.context = context;
        this.items = items != null ? items : new LinkedList<String>();
    }

    public void setDataSource(List<String> items){
        this.items = items != null ? items : new LinkedList<String>();
        this.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new LogViewHolder(LayoutInflater.from(context).inflate(R.layout.view_holder_log, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.vh_log_data.setText(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class LogViewHolder extends RecyclerView.ViewHolder {

        private TextView vh_log_data;

        LogViewHolder(View itemView) {
            super(itemView);
            this.vh_log_data = itemView.findViewById(R.id.vh_log_data);
        }
    }

}
