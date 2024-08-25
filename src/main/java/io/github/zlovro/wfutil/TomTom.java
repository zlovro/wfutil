package io.github.zlovro.wfutil;

import com.google.gson.Gson;
import net.minecraft.util.text.TextFormatting;

import java.awt.geom.Point2D;
import java.util.function.Consumer;

public class TomTom
{
    public class Summary
    {
        public int queryTime, numResults;
    }

    public class ReverseGeocodingResponse
    {
        public class Address
        {
            public String freeformAddress;
        }

        public class AddressWrapper
        {
            public Address address;
        }

        public Summary summary;
        public AddressWrapper[] addresses;
    }

    public class GeocodingResponse
    {
        public class Response
        {
            public class Position
            {
                public float lat, lon;
            }

            public Position position;
        }

        public Summary summary;
        public Response[] results;
    }

    public static final String API_KEY = "horDmCtr6GKavhOERTOMegyOssm7dLLQ";
    public static final Gson GSON = new Gson();

    public static void reverseGecode(Point2D.Double latLon, Consumer<String> callback)
    {
        String url = String.format("https://api.tomtom.com/search/2/reverseGeocode/%f,%f.json?key=%s&radius=4300&position=45.42929873257377,17.2265625", latLon.y, latLon.x, API_KEY);
        byte[] bytes = WFMod.download(url, 2000);
        if (bytes.length == 0)
        {
            callback.accept(String.format("%sQuery timed out: %s%s", TextFormatting.RED, TextFormatting.BOLD, url));
            return;
        }

        String response = new String(bytes);
        ReverseGeocodingResponse parsed = GSON.fromJson(response, ReverseGeocodingResponse.class);
        if (parsed.summary.numResults == 0)
        {
            callback.accept(TextFormatting.RED + "Invalid query");
            return;
        }

        callback.accept(parsed.addresses[0].address.freeformAddress);
    }

    public static void geocodeAsync(String query, Consumer<GeocodingResponse.Response.Position> callback)
    {
        String url = String.format("https://api.tomtom.com/search/2/geocode/%s.json?key=%s&radius=4300&position=45.42929873257377,17.2265625", query, API_KEY);
        byte[] bytes = WFMod.download(url, 2000);
        if (bytes.length == 0)
        {
            callback.accept(null);
            return;
        }

        String response = new String(bytes);
        GeocodingResponse parsed = GSON.fromJson(response, GeocodingResponse.class);
        if (parsed.summary.numResults == 0)
        {
            callback.accept(null);
            return;
        }

        callback.accept(parsed.results[0].position);
    }
}
