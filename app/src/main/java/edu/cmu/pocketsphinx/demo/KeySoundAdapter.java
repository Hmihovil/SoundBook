package edu.cmu.pocketsphinx.demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class KeySoundAdapter extends BaseAdapter {
    private final ArrayList mData;
    private Map<String, String> mMap;

    public KeySoundAdapter(Map<String, String> map) {
        mData = new ArrayList();
        mData.addAll(map.entrySet());
        mMap = map;
    }

    public void clear() {
        mMap.clear();
        mData.clear();
        mData.addAll(mMap.entrySet());
    }

    public void put(String key, String value) {
        mMap.put(key, value);
        mData.clear();
        mData.addAll(mMap.entrySet());
    }

    public boolean containsKey(Object key) {
        return mMap.containsKey(key);
    }

    public String get(Object key) {
        return mMap.get(key);
    }

    public int size() {
        return mMap.size();
    }

    public Set<Map.Entry<String, String>> entrySet() {
        return mMap.entrySet();
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Map.Entry<String, String> getItem(int position) {
        return (Map.Entry) mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View result;

        if (convertView == null) {
            result = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_key_sound, parent, false);
        } else {
            result = convertView;
        }

        Map.Entry<String, String> item = getItem(position);
        ((TextView) result.findViewById(R.id.text)).setText(item.getKey());

        return result;
    }
}
