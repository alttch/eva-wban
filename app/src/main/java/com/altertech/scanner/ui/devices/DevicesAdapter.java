package com.altertech.scanner.ui.devices;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.altertech.scanner.service.BluetoothLeService;
import com.altertech.scanner.R;
import com.altertech.scanner.core.device.Device;
import com.altertech.scanner.utils.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by oshevchuk on 26.07.2018
 */
public class DevicesAdapter extends RecyclerView.Adapter<DevicesAdapter.DevicesViewHolder> {

    private Context context;
    private List<Device> devices;

    public DevicesAdapter(Context context, List<Device> devices) {
        this.context = context;
        this.devices = devices != null ? devices : new ArrayList<Device>();
    }

    @NonNull
    @Override
    public DevicesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DevicesViewHolder(LayoutInflater.from(context).inflate(R.layout.view_holder_device, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final DevicesViewHolder holder, final int position) {
        holder.vh_device_name.setText(StringUtil.isNotEmpty(this.devices.get(position).getName()) ? this.devices.get(position).getName() : "empty");
        holder.vh_device_address.setText(StringUtil.isNotEmpty(this.devices.get(position).getAddress()) ? this.devices.get(position).getAddress() : "empty");
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Activity activity = ((Activity)context);
                activity.setResult(Activity.RESULT_OK, new Intent().putExtra(BluetoothLeService.EXTRA_DATA, devices.get(position)));
                activity.finish();
            }
        });
    }

    @Override
    public int getItemCount() {
        return this.devices.size();
    }

    class DevicesViewHolder extends RecyclerView.ViewHolder {

        private TextView vh_device_name;
        private TextView vh_device_address;

        DevicesViewHolder(View itemView) {
            super(itemView);
            this.vh_device_name = itemView.findViewById(R.id.vh_device_name);
            this.vh_device_address = itemView.findViewById(R.id.vh_device_address);
        }
    }
}
