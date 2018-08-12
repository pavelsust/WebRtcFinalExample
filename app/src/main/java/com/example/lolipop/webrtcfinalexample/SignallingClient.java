package com.example.lolipop.webrtcfinalexample;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Webrtc_Step3
 * Created by vivek-3102 on 11/03/17.
 */

class SignallingClient {
    private static SignallingClient instance;
    private String roomName = null;
    private Socket socket;
    boolean isChannelReady = false;
    boolean isInitiator = false;
    boolean isStarted = false;
    private SignalingInterface callback;
    private Context context;
    Dialog dialog ;


    //This piece of code should not go into production!!
    //This will help in cases where the node server is running in non-https server and you want to ignore the warnings
    @SuppressLint("TrustAllX509TrustManager")
    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) {
        }
    }};

    public static SignallingClient getInstance() {
        if (instance == null) {
            instance = new SignallingClient();
        }
        if (instance.roomName == null) {
            //set the room name here
            instance.roomName = "voip message";
        }
        return instance;
    }


    private Emitter.Listener onConnect = new Emitter.Listener() {

        JSONObject registerInfo = new JSONObject();
        @Override
        public void call(Object... args) {

            try {
                registerInfo.put("name" , "one plus user");
                registerInfo.put("id" , "new user id");
                registerInfo.put("specialty" , "special");

            } catch (JSONException e) {
                e.printStackTrace();
            }

            socket.emit("register", registerInfo);
            getUserList();

        }
    };



    private void getUserList(){

        String[] listUser = {};
        String[] list = new String[1];


        socket.emit("list users", new String[0], args -> {
            Log.d("SignallingClient", "list of users call() called with: args = " + Arrays.toString(args) + "");

            ((MainActivity) context).runOnUiThread(new Runnable() {
                public void run() {
                    List<String> nameList = new ArrayList<String>();
                    List<String> idList = new ArrayList<String>();

                    dialog = new Dialog(context);
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Select user");

                    ListView modeList = new ListView(context);
                    //  String data =(JsonArray)args;
                    try {
                        JSONObject obj = new JSONObject(args[0].toString());
                        Iterator<String> keys = obj.keys();
                        //   Iterator<String> values = obj.vali();
                        int i = 0;

                        while (keys.hasNext()) {
                            String key = (String) keys.next(); // First key in your json object
                            String value = obj.getString(key);
                            JSONObject obj2 = new JSONObject(value);
                            nameList.add(obj2.getString("name"));
                            idList.add(obj2.getString("id"));
                            i++;
                        }
                        // obj.get(0)
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    String[] stringArray = new String[nameList.size()];
                    nameList.toArray(stringArray);

                    ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, android.R.id.text1, stringArray);
                    modeList.setAdapter(modeAdapter);

                    builder.setView(modeList);
                    dialog = builder.create();
                    dialog.show();

                    isInitiator = true;
                    isChannelReady = true;

                    modeList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            Toast.makeText(context, idList.get(i).toString(), Toast.LENGTH_LONG).show();
                            ((MainActivity) context).start();
                            dialog.dismiss();
                        }
                    });
                }
            });

        });

    }


    public void init(Context context,SignalingInterface signalingInterface) {
        this.context = context;
        this.callback = signalingInterface;

            emitInitStatement();

        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslcontext);
            //set the socket.io url here
            socket = IO.socket("http://139.59.248.179:8000/");
            socket.connect();
            Log.d("SignallingClient", "init() called");

            socket.on(Socket.EVENT_CONNECT, onConnect);



            //room created event.
            socket.on("new user", args -> {
                Log.d("SignallingClient", "New user call() called with: args = [" + Arrays.toString(args) + "]");
                isInitiator = true;
                callback.onCreatedRoom();

            });



            //room is full event
            //    socket.on("new user", args -> Log.d("SignallingClient", "New users call() called with: args = [" + Arrays.toString(args) + "]"));
            socket.on("user leave", args -> Log.d("SignallingClient", "leave users call() called with: args = [" + Arrays.toString(args) + "]"));
            socket.on("list user", args -> Log.d("SignallingClient", "list of users call() called with: args = [" + Arrays.toString(args) + "]"));
            socket.on("hello", args -> Log.d("SignallingClient", "list of user call() called with: args = [" + Arrays.toString(args) + "]"));

            //peer joined event
            socket.on("voip message", args -> {

                Log.d("SignallingClient", "join call() called with: args = [" + Arrays.toString(args) + "]");

                callback.onNewPeerJoined();
            });

            //when you joined a chat room successfully
            socket.on("list users", args -> {
                Log.d("SignallingClient", "list users call() called with: args = [" + Arrays.toString(args) + "]");
                Dialog dialog = new Dialog(context);
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Select Color Mode");

                ListView modeList = new ListView(context);
                String[] stringArray = new String[] { "Bright Mode", "Normal Mode" };
                ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, android.R.id.text1, stringArray);
                modeList.setAdapter(modeAdapter);

                builder.setView(modeList);
                dialog = builder.create();
            });

            //log event
            socket.on("log", args -> Log.d("SignallingClient", "log call() called with: args = [" + Arrays.toString(args) + "]"));

            //bye event
            socket.on("bye", args -> callback.onRemoteHangUp((String) args[0]));


            //messages - SDP and ICE candidates are transferred through this
            socket.on("chat message", args -> {
                Log.d("SignallingClient", "message call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    Log.d("SignallingClient", "String received :: " + args[0]);
                    String data = (String) args[0];

                    if (data.equalsIgnoreCase("")) {
                        callback.onTryToStart();
                    }
                    if (data.equalsIgnoreCase("bye")) {
                        callback.onRemoteHangUp(data);
                    }
                } else if (args[0] instanceof JSONObject) {
                    try {

                        JSONObject data = (JSONObject) args[0];
                        Log.d("SignallingClient", "Json Received :: " + data.toString());
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            callback.onOfferReceived(data);
                        } else if (type.equalsIgnoreCase("answer") && isStarted) {
                            callback.onAnswerReceived(data);
                        } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            callback.onIceCandidateReceived(data);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void emitInitStatement(){
        
        JSONObject jsonObject2 = new JSONObject();
        try{
            jsonObject2.put("hasVideo" , "true or false");
            jsonObject2.put("handle" , "from id");
        }catch (Exception e){
            e.printStackTrace();
        }

        JSONArray jsonArrayPayload = new JSONArray();
        jsonArrayPayload.put(jsonObject2);


        JSONObject jsonObject1 = new JSONObject();
        try{
            jsonObject1.put("id" , "user_id");
            jsonObject1.put("payload" , jsonArrayPayload);
        }catch (Exception e){
            e.printStackTrace();
        }

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(jsonObject1);

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("params" , jsonArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d("JSON" , ""+jsonObject.toString());



    }

    public void emitMessage(String message) {
        Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
        socket.emit("message", message);
    }

    public void emitMessage(SessionDescription message) {
        try {
            Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            obj.put("type", message.type.canonicalForm());
            obj.put("sdp", message.description);
            Log.d("emitMessage", obj.toString());
            socket.emit("chat message", obj);
            Log.d("biplob", obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            socket.emit("chat message", object);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void close() {
        socket.emit("bye", roomName);
        socket.disconnect();
        socket.close();
    }


    interface SignalingInterface {
        void onRemoteHangUp(String msg);

        void onOfferReceived(JSONObject data);

        void onAnswerReceived(JSONObject data);

        void onIceCandidateReceived(JSONObject data);

        void onTryToStart();

        void onCreatedRoom();

        void onJoinedRoom();

        void onNewPeerJoined();
    }
}
