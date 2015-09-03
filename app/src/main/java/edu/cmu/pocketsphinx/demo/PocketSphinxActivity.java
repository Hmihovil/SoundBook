/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import static android.widget.Toast.makeText;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

public class PocketSphinxActivity extends Activity implements RecognitionListener {
    private static final String TAG = PocketSphinxActivity.class.getSimpleName();
    private static final String FILENAME = "keywords.txt";
    private static final int ADD_REQUEST_CODE = 0x200;
    private static final String KEYWORD_SEARCH = "keyword_search";
    private static final String DEFAULT_KEYWORD = "thunder";

    private SpeechRecognizer recognizer;
    private HashMap<String, String> mKeySounds = new HashMap<String, String>();
    private KeySoundAdapter mAdapter = new KeySoundAdapter(mKeySounds);
    private String mKeyword;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // add default keysounds
        addDefaultKeySound("storm is coming", "thunder");
        addDefaultKeySound("everyone applauded", "applause");
        addDefaultKeySound("crickets", "crickets");
        addDefaultKeySound("crowd laughing", "crowd_laughing");
        addDefaultKeySound("ringed the door bell", "door_bell");
        addDefaultKeySound("building a table", "hammering");
        addDefaultKeySound("received a call", "phone_ringing");
        addDefaultKeySound("birds and monkeys", "birds_and_monkeys");

        ListView mList = (ListView) findViewById(R.id.list);
        mList.setAdapter(mAdapter);

        Button addBtn = (Button) findViewById(R.id.add);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddDialog();
            }
        });

        Button deleteAllBtn = (Button) findViewById(R.id.delete_all);
        deleteAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteAllKeySounds();
            }
        });

        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(PocketSphinxActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    Toast.makeText(getApplicationContext(), "Failed to init recognizer " + result, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Ready!", Toast.LENGTH_SHORT).show();
                    listen();
                }
            }
        }.execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recognizer.cancel();
        recognizer.shutdown();
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        Iterator it = mAdapter.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = (Map.Entry<String, String>) it.next();
            String keyword = pair.getKey();
            if (text.equals(keyword)) {
                listen();
            }
        }

        Log.d(TAG, text);
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            Iterator it = mAdapter.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> pair = (Map.Entry<String, String>) it.next();
                String keyword = pair.getKey();
                String key = keyword;
                int last = key.indexOf("/1e-");
                if (last >= 0) {
                    key = key.substring(0, last);
                }
                if (text.indexOf(key) >= 0) {
                    Log.i(TAG, "Playing sound");
                    playSound(keyword);
                    listen();
                    break;
                }
                Log.i(TAG, "Testing text '" + text + "' with key '" + key + "'");
            }
            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(TAG, "Speech starting");
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        Log.i(TAG, "Reached end of speech");
        listen();
    }

    private void listen() {
        if (recognizer != null) {
            recognizer.stop();
            if (mAdapter.size() > 0) {
                recognizer.startListening(KEYWORD_SEARCH, 5000);
            }
        }
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setKeywordThreshold(1e-45f) // Threshold to tune for keyphrase to balance between false alarms and misses
                .setBoolean("-allphone_ci", true)// Use context-independent phonetic search, context-dependent is too slow for mobile
                .getRecognizer();
        recognizer.addListener(this);

        loadRecognizerKeywordsFile();
    }

    @Override
    public void onError(Exception error) {
        Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTimeout() {
        listen();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == ADD_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                String uriStr = uri.toString();
                Log.i(TAG, "Uri: " + uriStr);
                saveKeySound(mKeyword, uriStr);
            }
        }
    }

    private void showAddDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.dialog_add, null);

        mKeyword = null;

        builder.setMessage(R.string.add_dialog_message);
        builder.setTitle(R.string.add_dialog_title);
        builder.setView(view);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                EditText edit = (EditText) view.findViewById(R.id.keyword);
                String keyword = edit.getText().toString();
                if (keyword.length() < 3) {
                    Toast.makeText(getApplicationContext(), "Keyword length is too short!", Toast.LENGTH_SHORT).show();
                }

                String[] words = keyword.split(" ");
                for (String word : words) {
                    if (recognizer.getDecoder().lookupWord(word) == null) {
                        Toast.makeText(getApplicationContext(), "Couldn't find the word '" + word + "' in dictionary", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                chooseKeySound(keyword);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });
        AlertDialog dialog = builder.create();

        dialog.show();
    }

    private void addDefaultKeySound(String s, String filename) {
        String keyword = s + calculateThreshold(s) + "\n";
        saveKeySound(keyword, filename);
    }

    private void chooseKeySound(String keyword) {
        mKeyword = keyword + calculateThreshold(keyword) + "\n";

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");

        startActivityForResult(intent, ADD_REQUEST_CODE);
    }

    private void saveKeySound(String keyword, String uri) {
        mAdapter.put(keyword, uri);
        mAdapter.notifyDataSetChanged();

        saveRecognizerKeywordsFile();
        loadRecognizerKeywordsFile();
    }

    private void deleteAllKeySounds() {
        mAdapter.clear();
        mAdapter.notifyDataSetChanged();

        saveRecognizerKeywordsFile();
        loadRecognizerKeywordsFile();
    }

    private void playSound(String keyword) {
        String uriStr = mAdapter.get(keyword);
        if (!uriStr.contains("//")) {
            int resID = getResources().getIdentifier(uriStr, "raw", getPackageName());
            MediaPlayer mediaPlayer = MediaPlayer.create(this, resID);
            mediaPlayer.start();
        } else {
            Uri uri = Uri.parse(uriStr);
            Log.i(TAG, "Playing sound: " + uriStr + " triggered by '" + keyword + "'");
            Ringtone ring = RingtoneManager.getRingtone(this, uri);
            if (ring != null) {
                ring.play();
            }
        }
    }

    private void saveRecognizerKeywordsFile() {
        try {
            FileOutputStream fos = openFileOutput(FILENAME, Context.MODE_PRIVATE);
            Iterator it = mAdapter.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> pair = (Map.Entry<String, String>) it.next();
                String key = pair.getKey();
                fos.write(key.getBytes());
                Log.i(TAG, "Saved keyword: " + key);
            }
            fos.close();
            Log.i(TAG, "Saved " + mAdapter.size() + " keywords!");
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "File not found: " + FILENAME, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void loadRecognizerKeywordsFile() {
        if (recognizer != null && mAdapter.size() > 0) {
            recognizer.stop();

            File file = new File(getFilesDir(), FILENAME);
            if (file.exists()) {
                Log.i(TAG, "Setting keyword list");
                recognizer.addKeywordSearch(KEYWORD_SEARCH, file);
            }

            listen();
        }
    }

    private String calculateThreshold(String key) {
        final int max = 5;
        final int mult = 5;
        int n = 1;
        int len = key.length();

        for (int i = 1; i < max; i++) {
            if (len >= i * mult && len < (i + 1) * mult) {
                n = i * 10;
            } else if (len >= max * mult) {
                n = max * mult;
            }
        }

        n += (key.split(" ").length - 1) * 10;

        return "/1e-" + n + "/";
    }
}
