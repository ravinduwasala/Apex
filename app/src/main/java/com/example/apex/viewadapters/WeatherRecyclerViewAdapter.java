// Ravindu

package com.example.apex.viewadapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.apex.R;
import com.example.apex.databinding.LayoutNavigationRecyclerviewItemBinding;
import com.example.apex.models.WeatherWrapper;

import java.util.ArrayList;
import java.util.List;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

public class WeatherRecyclerViewAdapter extends RecyclerView.Adapter<WeatherRecyclerViewAdapter.WeatherRecyclerViewHolder> {

    private final Context context;
    private List<WeatherWrapper> weatherWrapperList; //get API weather data to list

    public WeatherRecyclerViewAdapter(Context context) { //initialize the recycle view list.
        this.context = context;
    }

    @NonNull
    @Override
    //layout navigation recycle view item binding
    public WeatherRecyclerViewAdapter.WeatherRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutNavigationRecyclerviewItemBinding binding = LayoutNavigationRecyclerviewItemBinding.inflate(LayoutInflater.from(context), parent, false);
        return new WeatherRecyclerViewHolder(binding);
    }

    @Override  //set the relevant weather data to the relevant place
    public void onBindViewHolder(@NonNull WeatherRecyclerViewAdapter.WeatherRecyclerViewHolder holder, int position) {
        WeatherWrapper weatherWrapper = weatherWrapperList.get(position);  //assign weather details to the list item
        holder.bind(weatherWrapper, position);
    }

    @Override
    public int getItemCount() {
        return weatherWrapperList == null ? 0 : weatherWrapperList.size(); // get the list size if empty =0 else get the size of the list
    }

    public void setWeatherWrapperList(List<WeatherWrapper> weatherWrapperList) {
        this.weatherWrapperList = weatherWrapperList;  //set te list
        notifyDataSetChanged();
    }

    public void emptyWeatherWrapperList() {  //empty the list when resetting the location
        this.weatherWrapperList = null;
        notifyDataSetChanged();
    }

    public void collapseCards() { //close all the cards when bottom sheet slide down
        if (weatherWrapperList != null) {
            List<Integer> list = new ArrayList<>();

            for (int i = 0; i < weatherWrapperList.size(); i++) {
                if (weatherWrapperList.get(i).getWeather().isExpanded()) {
                    weatherWrapperList.get(i).getWeather().setExpanded(false);
                    list.add(i);
                }
            }

            for (int i : list) {
                notifyItemChanged(i);
            }
        }
    }

    public class WeatherRecyclerViewHolder extends RecyclerView.ViewHolder {

        private final LayoutNavigationRecyclerviewItemBinding binding;

        public WeatherRecyclerViewHolder(@NonNull LayoutNavigationRecyclerviewItemBinding binding) { //class for list item
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(WeatherWrapper weatherWrapper, int position) {  //set the binding to the list item
            binding.setWeatherWrapper(weatherWrapper);
            binding.setPosition(position);
            binding.executePendingBindings();

            Glide.with(context)            // set photo to weather  (cloud)
                    .load(weatherWrapper.getWeather().getWeatherDescriptions().get(0).getIconUrl())
                    .transition(withCrossFade())
                    .fitCenter()
                    .error(R.drawable.ic_baseline_error_outline_24)
                    .fallback(R.drawable.ic_baseline_error_outline_24)
                    .into(binding.imgIcon);

            binding.card.setOnClickListener(v -> {
                List<Integer> list = new ArrayList<>();

                //expand the cards
                for (int i = 0; i < weatherWrapperList.size(); i++) {
                    if (weatherWrapperList.get(i).getWeather().equals(weatherWrapper.getWeather())) {  //check the clicked one is i if so expand it
                        weatherWrapper.getWeather().setExpanded(!weatherWrapper.getWeather().isExpanded());
                        list.add(i);
                    } else {
                        if (weatherWrapperList.get(i).getWeather().isExpanded()) {   //else wrap up
                            weatherWrapperList.get(i).getWeather().setExpanded(false);
                            list.add(i);
                        }
                    }
                }

                for (int i : list) {
                    notifyItemChanged(i);
                }
            });
        }
    }
}
