package com.sativa.ssh4android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;

public class CustomAdapter extends ArrayAdapter<String> {

    private final List<String> fileList;
    private final String GO_UP;
    private final Context context;

    public CustomAdapter(Context context, int resource, List<String> objects, String goUp) {
        super(context, resource, objects);
        this.context = context;
        this.fileList = objects;
        this.GO_UP = goUp;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView textView = view.findViewById(android.R.id.text1);

        // Check if the item is the "GO_UP" item
        if (fileList.get(position).equals(GO_UP)) {
            int colorRes = ContextCompat.getColor(context, R.color.CAT);
            textView.setTextColor(colorRes);
        }
        return view;
    }
}