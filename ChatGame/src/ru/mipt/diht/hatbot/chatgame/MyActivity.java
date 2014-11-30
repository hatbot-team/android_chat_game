package ru.mipt.diht.hatbot.chatgame;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MyActivity extends Activity implements OnInitListener {

    protected static final int REQUEST_OK = 1;

    private TextToSpeech textToSpeech;
    private String host = "http://hatbot.me";
    private String correctWord = "";
    private String randomWord = "/random_word";
    private List<String> savedChat;

    //Override standard methods

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        savedChat = new ArrayList<String>();
        textToSpeech = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int code) {
        if (code == TextToSpeech.SUCCESS) {
            textToSpeech.setLanguage(new Locale("ru"));
        } else {
            textToSpeech = null;
            Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    public void startGame(View v) {
        if (internetCheck()) {
            //seems done
            //TODO change layout to inGameLayout
            LinearLayout inGameLayout = (LinearLayout)findViewById(R.id.inGameLayout);
            inGameLayout.setVisibility(View.VISIBLE);
            LinearLayout startLayout = (LinearLayout)findViewById(R.id.startLayout);
            startLayout.setVisibility(View.INVISIBLE);
            (new TitleTask()).execute(host + randomWord);
        }
    }

    public void sendUserAnswer(View v) {
        if (internetCheck()) {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, new Locale("ru"));
            final Context context = this;
            try {
                startActivityForResult(i, REQUEST_OK);
            } catch (Exception e) {
                showToast("Ошибка встроенного распознавателя речи", context);
            }
        }
    }

    public void continueGame(View v)     {
        if (internetCheck()) {
            LinearLayout inGameLayout = (LinearLayout)findViewById(R.id.inGameLayout);
            inGameLayout.setVisibility(View.VISIBLE);
            LinearLayout continueGameLayout = (LinearLayout)findViewById(R.id.continueGameLayout);
            continueGameLayout.setVisibility(View.INVISIBLE);
            //TODO change layout to inGameLayout
            (new TitleTask()).execute(host + randomWord);
        }
    }

    //Override method for google speech to text, it works when user says something

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==REQUEST_OK  && resultCode==RESULT_OK) {
            ArrayList<String> userAnswers = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String answer = "";
            for (String token : userAnswers) {
                answer += token;
            }
            savedChat.add(answer);
            if (answer.compareTo(correctWord) == 0) {
                LinearLayout continueGameLayout = (LinearLayout)findViewById(R.id.continueGameLayout);
                continueGameLayout.setVisibility(View.VISIBLE);
                LinearLayout inGameLayout = (LinearLayout)findViewById(R.id.inGameLayout);
                inGameLayout.setVisibility(View.INVISIBLE);
                //TODO users answer is correct - change layout to continueGameLayout
            } else {
                LinearLayout loseGameLayout = (LinearLayout)findViewById(R.id.loseGameLayout);
                loseGameLayout.setVisibility(View.VISIBLE);
                LinearLayout inGameLayout = (LinearLayout)findViewById(R.id.inGameLayout);
                inGameLayout.setVisibility(View.INVISIBLE);
                //TODO users answer isn't correct - try to find 1 more explanation
                //TODO if there is no more explanations for this word - change layout to loseGameLayout
            }
        }
    }

    //Get explanation with AsyncTask

    private class TitleTask extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... params) {
            try {
                HttpClient httpclient = new DefaultHttpClient();

                HttpGet request = new HttpGet();
                URI website = new URI(params[0]);
                request.setURI(website);
                HttpResponse response = httpclient.execute(request);

                return new BufferedReader(new InputStreamReader(
                        response.getEntity().getContent())).readLine();
            } catch (Exception e) {
                Log.wtf("except", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            correctWord = result;
            (new DefinitionTask()).execute(host + "/explain?word=" + result);
            super.onPostExecute(result);
        }
    }

    private class DefinitionTask extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... params) {
            try {
                HttpClient httpclient = new DefaultHttpClient();

                HttpGet request = new HttpGet();
                URI website = new URI(params[0]);
                request.setURI(website);
                HttpResponse response = httpclient.execute(request);
                Log.d("DefinitionTask", response.toString());

                return new BufferedReader(new InputStreamReader(
                        response.getEntity().getContent())).readLine();
            } catch (Exception e) {
                Log.e("except", e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            //result = "{\"text\":\"english русский\"}";
            Log.d("onPostExecute", "I am here");
            try {
                JSONObject json = new JSONObject(result);
                savedChat.add(json.getString("text"));
                if (textToSpeech != null) {
                    //if (!textToSpeech.isSpeaking()) {
                        textToSpeech.speak(json.getString("text"), TextToSpeech.QUEUE_FLUSH, null);
                    //}
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            super.onPostExecute(result);
        }

    }

    //This function shows toast messages to user

    public void showToast(final String toast, final Context context) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    //Internet Check function and class

    private boolean internetCheck() {
        ConnectionDetector connectionDetector = new ConnectionDetector(getApplicationContext());
        final Context context = this;
        if (connectionDetector.isConnectingToInternet()) {
            return true;
        } else {
            showToast("Нет подключения к интернету!", context);
            return false;
        }
    }

    public class ConnectionDetector {

        private Context _context;

        public ConnectionDetector(Context context) {
            this._context = context;
        }

        public boolean isConnectingToInternet() {
            ConnectivityManager connectivity = (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                NetworkInfo[] info = connectivity.getAllNetworkInfo();
                if (info != null)
                    for (NetworkInfo anInfo : info)
                        if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                            return true;
                        }

            }
            return false;
        }
    }
}
