package com.fongmi.android.tv.model;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HotTvViewModel extends ViewModel {

    private static final String URL = "https://m.douban.com/rexxar/api/v2/subject/recent_hot";
    private static final long TIMEOUT_MS = 8_000;

    public enum Section {
        SERIES(R.string.home_chart_series, Option.DOMESTIC, Option.WESTERN, Option.JAPANESE, Option.KOREAN, Option.DOCUMENTARY),
        MOVIE(R.string.home_chart_movie, Option.HOT, Option.LATEST, Option.HIGH_SCORE, Option.HIDDEN_GEMS),
        VARIETY(R.string.home_chart_variety, Option.SHOW_DOMESTIC, Option.SHOW_FOREIGN);

        public final int label;
        public final List<Option> options;

        Section(int label, Option... options) {
            this.label = label;
            this.options = List.of(options);
        }

        public Chart firstChart() {
            return new Chart(this, options.get(0), Option.CHINESE);
        }
    }

    public enum Option {
        DOMESTIC(R.string.home_chart_domestic, "tv_domestic"),
        WESTERN(R.string.home_chart_western, "tv_american"),
        JAPANESE(R.string.home_chart_japanese, "tv_japanese"),
        KOREAN(R.string.home_chart_korean, "tv_korean"),
        DOCUMENTARY(R.string.home_chart_documentary, "tv_documentary"),
        SHOW_DOMESTIC(R.string.home_chart_show_domestic, "show_domestic"),
        SHOW_FOREIGN(R.string.home_chart_show_foreign, "show_foreign"),
        HOT(R.string.home_chart_movie_hot, "热门"),
        LATEST(R.string.home_chart_movie_latest, "最新"),
        HIGH_SCORE(R.string.home_chart_movie_high_score, "豆瓣高分"),
        HIDDEN_GEMS(R.string.home_chart_movie_hidden_gems, "冷门佳片"),
        CHINESE(R.string.home_chart_region_chinese, "华语"),
        EUROPE_AMERICA(R.string.home_chart_region_western, "欧美"),
        SOUTH_KOREA(R.string.home_chart_region_korea, "韩国"),
        JAPAN(R.string.home_chart_region_japan, "日本");

        public static final List<Option> MOVIE_REGIONS = List.of(CHINESE, EUROPE_AMERICA, SOUTH_KOREA, JAPAN);

        public final int label;
        final String parameter;

        Option(int label, String parameter) {
            this.label = label;
            this.parameter = parameter;
        }
    }

    public record Chart(Section section, Option option, Option region) {
        String url() {
            String category = switch (section) {
                case SERIES -> "tv";
                case VARIETY -> "show";
                case MOVIE -> option.parameter;
            };
            return Uri.parse(URL).buildUpon()
                    .appendPath(section == Section.MOVIE ? "movie" : "tv")
                    .appendQueryParameter("start", "0")
                    .appendQueryParameter("limit", "20")
                    .appendQueryParameter("category", category)
                    .appendQueryParameter("type", section == Section.MOVIE ? region.parameter : option.parameter)
                    .build().toString();
        }
    }

    private final MutableLiveData<List<Vod>> items = new MutableLiveData<>();
    private final Map<Chart, List<Vod>> cache = new HashMap<>();
    private Chart chart = Section.SERIES.firstChart();
    private Call call;

    public LiveData<List<Vod>> getItems() {
        return items;
    }

    public Chart getChart() {
        return chart;
    }

    public void load(Chart next, boolean refresh) {
        if (!refresh && chart.equals(next) && call != null) return;
        if (call != null) call.cancel();
        call = null;
        chart = next;

        if (refresh) cache.remove(next);
        List<Vod> cached = cache.get(next);
        if (cached != null) {
            items.setValue(cached);
            return;
        }

        items.setValue(null);
        call = OkHttp.newCall(next.url(), Map.of("Referer", next.section() == Section.MOVIE
                ? "https://movie.douban.com/explore" : "https://movie.douban.com/tv/"));
        call.timeout().timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call request, IOException error) {
                publish(request, Collections.emptyList());
            }

            @Override
            public void onResponse(Call request, Response response) {
                List<Vod> result = Collections.emptyList();
                try (response) {
                    ResponseBody body = response.body();
                    if (response.isSuccessful() && body != null) result = parse(body.string());
                } catch (IOException | RuntimeException ignored) { }
                publish(request, result);
            }
        });
    }

    private void publish(Call request, List<Vod> result) {
        App.post(() -> {
            if (request != call || request.isCanceled()) return;
            call = null;
            if (!result.isEmpty()) cache.put(chart, result);
            items.setValue(result);
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
        call = null;
    }
}
