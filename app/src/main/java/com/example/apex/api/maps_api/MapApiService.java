// Ravindu

package com.example.apex.api.maps_api;

import com.example.apex.models.GeoCode;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface MapApiService {
    @GET("geocode/json")
        //return ge0 cde 0bject f0r given latlang
    Observable<GeoCode> getGeocode(@Query("latlng") String latlng);
}
