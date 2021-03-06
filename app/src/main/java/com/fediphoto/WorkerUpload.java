package com.fediphoto;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

public class WorkerUpload extends Worker {
    private final String TAG = this.getClass().getCanonicalName();
    private final Context context;

    public WorkerUpload(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, String.format("WorkerUpload started %s", new Date()));
        Data data = getInputData();
        String fileName = data.getString(MainActivity.Literals.fileName.name());
        if (Utils.isBlank(fileName)) {
            Toast.makeText(context, "File name is blank.", Toast.LENGTH_LONG).show();
            return Result.failure();
        }
        File file = new File(fileName);
        Log.i(TAG, String.format("File name %s file exists %s", fileName, file.exists()));
        if (!file.exists()) {
            Toast.makeText(context, String.format("File \"%s\" does not exist.", file.getAbsoluteFile()), Toast.LENGTH_LONG).show();
            return Result.failure();
        }
        JsonElement account = Utils.getAccountSelectedFromSettings(context);
        String instance = Utils.getProperty(account, MainActivity.Literals.instance.name());
        String token = Utils.getProperty(account, MainActivity.Literals.access_token.name());
        Log.i(TAG, String.format("WorkerUploadWorkerUpload account: %s Instance: %s Token: %s", Utils.getProperty(account, MainActivity.Literals.display_name.name()), instance, token));
        String boundary = new BigInteger(256, new Random()).toString();
        String urlString = String.format("https://%s/api/v1/media", instance);
        HttpsURLConnection urlConnection;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        PrintWriter writer = null;
        StringBuilder responseBody = new StringBuilder();
        try {
            URL url = new URL(urlString);
            Log.i(TAG, String.format("URL: %s", url.toString()));
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Cache-Control", "no-cache");
            urlConnection.setRequestProperty("User-Agent", "FediPhoto");
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod(MainActivity.Literals.POST.name());
            urlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            urlConnection.setDoOutput(true);
            // test
            String authorization = String.format("Bearer %s", token);
            urlConnection.setRequestProperty("Authorization", authorization);
            // end test
            outputStream = urlConnection.getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);
            writer.append("--").append(boundary).append(Utils.LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"access_token\"").append(Utils.LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(Utils.LINE_FEED);
            writer.append(Utils.LINE_FEED).append(token).append(Utils.LINE_FEED).flush();

            writer.append("--").append(boundary).append(Utils.LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"");
            writer.append(file.getName()).append("\"").append(Utils.LINE_FEED);
            writer.append("Content-Type: image/jpeg").append(Utils.LINE_FEED);
            writer.append("Content-Transfer-Encoding: binary").append(Utils.LINE_FEED);
            writer.append(Utils.LINE_FEED).flush();


            inputStream = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            inputStream.close();

       //     writer.append(Utils.LINE_FEED).flush();


            writer.append(Utils.LINE_FEED).flush();
            writer.append("--").append(boundary).append("--").append(Utils.LINE_FEED);
            writer.close();

            outputStream.flush();

            int responseCode = urlConnection.getResponseCode();
            String responseCodeMessage = String.format(Locale.US, "WorkerUpload response code: %d\n", responseCode);
            Log.i(TAG, responseCodeMessage);
            urlConnection.setInstanceFollowRedirects(true);
            inputStream = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(isr);

            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
            reader.close();
            urlConnection.disconnect();
            JsonElement jsonElement = JsonParser.parseString(responseBody.toString());
            Log.i(TAG, String.format("WorkerUpload output from upload: %s", jsonElement));
            Data outputData = new Data.Builder()
                    .putString(MainActivity.Literals.id.name(), Utils.getProperty(jsonElement, MainActivity.Literals.id.name()))
                    .putString(MainActivity.Literals.fileName.name(), fileName)
                    .build();
            return Result.success(outputData);
        } catch (Exception e) {
            System.out.format("Error: %s\nResponse body: %s\n", e.getLocalizedMessage(), responseBody);
            e.printStackTrace();
            return Result.retry();
        } finally {
            Utils.close(inputStream, outputStream, writer);
        }

    }
}
