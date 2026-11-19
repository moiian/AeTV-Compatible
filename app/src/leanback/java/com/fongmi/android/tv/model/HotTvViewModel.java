package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HotTvViewModel extends ViewModel {

    private static final String URL = "https://m.douban.com/rexxar/api/v2/subject/recent_hot/tv?start=0&limit=20&category=tv&type=tv_domestic";
    private static final long TIMEOUT_MS = 8_000;

    private final MutableLiveData<List<Vod>> items = new MutableLiveData<>();
    private Call call;

    public LiveData<List<Vod>> getItems() {
        return items;
    }

    public void load() {
        if (call != null) return;
        call = OkHttp.newCall(URL, Map.of("Referer", "https://movie.douban.com/tv/"));
        call.timeout().timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call request, IOException error) {
                if (!request.isCanceled()) items.postValue(Collections.emptyList());
            }

            @Override
            public void onResponse(Call request, Response response) {
                List<Vod> result = Collections.emptyList();
                try (response) {
                    ResponseBody body = response.body();
                    if (response.isSuccessful() && body != null) result = parse(body.string());
                } catch (IOException | RuntimeException ignored) { }
                if (!request.isCanceled()) items.postValue(result);
            }
        });
    }

    static List<Vod> parse(String response) {
        JsonElement subjects = Json.safeObject(Json.parse(response)).get("items");
        if (!(subjects instanceof JsonArray list)) return Collections.emptyList();
        List<Vod> items = new ArrayList<>();
        for (JsonElement element : list) {
            if (!element.isJsonObject()) continue;
            JsonObject subject = element.getAsJsonObject();
            String id = Json.safeString(subject, "id");
            String title = Json.safeString(subject, "title");
            if (id.isEmpty() || title.isEmpty()) continue;
            Vod item = new Vod();
            item.setId(id);
            item.setName(title);
            JsonObject pictures = Json.safeObject(subject.get("pic"));
            String cover = Json.safeString(pictures, "large");
            if (cover.isEmpty()) cover = Json.safeString(pictures, "normal");
            if (!cover.isEmpty()) item.setPic(cover + "@Referer=https://movie.douban.com/");
            items.add(item);
        }
        return items;
    }

    @Override
    protected void onCleared() {
        if (call != null) call.cancel();
    }
}
