package pbell.offline.ole.org.pbell;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.DocumentChange;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.replicator.Replication;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenLogin extends AppCompatActivity {


    private View mContentView;

    private static final int REQUEST_READ_CONTACTS = 0;
    public static final String PREFS_NAME = "MyPrefsFile";

    SharedPreferences settings;
    CouchViews chViews = new CouchViews();
    String doc_lastVisit;
    private EditText mUsername;
    private EditText mPasswordView;
    private LoginActivity.UserLoginTask mAuthTask = null;

    String sys_oldSyncServerURL,sys_username,sys_lastSyncDate,
            sys_password,sys_usercouchId,sys_userfirstname,sys_userlastname,
            sys_usergender,sys_uservisits= "";
    Object[] sys_membersWithResource;
    private Dialog dialog;
    private ProgressDialog mDialog;


    final Context context = this;
    String[] databaseList = {"members","membercourseprogress","meetups","usermeetups","assignments",
            "calendar","groups","invitations","requests","shelf","languages"};

    Replication[] push = new Replication[databaseList.length];
    Replication[] pull= new Replication[databaseList.length];

    Database[] db = new Database[databaseList.length];
    Manager[] manager = new Manager[databaseList.length];
    boolean syncmembers,openMemberList= false;
    int syncCnt =0;
    AndroidContext androidContext;
    Database database;
    Replication pullReplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_login_new);
        mContentView = findViewById(R.id.fullscreen_content2);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        androidContext = new AndroidContext(this);

        /////////////////////////////////////
        // Set up the login form.
        mUsername = (EditText) mContentView.findViewById(R.id.txtUsername);
        mPasswordView = (EditText) findViewById(R.id.txtPassword);


        Button SignInButton = (Button) findViewById(R.id.btnSignIn);
        SignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                authenticateUser();
            }
        });

        Button SetupButton = (Button) findViewById(R.id.btnSetup);
        SetupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSyncURLDialog();
            }
        });

        restorePref();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    public boolean authenticateUser(){
        AndroidContext androidContext = new AndroidContext(this);
        Manager manager = null;
        try {
            manager = new Manager(androidContext, Manager.DEFAULT_OPTIONS);
            Database db = manager.getExistingDatabase("members");
            Query orderedQuery = chViews.CreateLoginByIdView(db).createQuery();
            orderedQuery.setDescending(true);
            QueryEnumerator results = orderedQuery.run();
            for (Iterator<QueryRow> it = results; it.hasNext();) {
                QueryRow row = it.next();
                String docId = (String) row.getValue();
                Document doc = db.getExistingDocument(docId);
                Map<String, Object> properties = doc.getProperties();
                String doc_loginId = (String) properties.get("login");
                String doc_password = (String) properties.get("password");

                if(mUsername.getText().toString().equals(doc_loginId)) {
                    Log.e("MYAPP", "Authentiicating User");

                    if (mPasswordView.getText().toString().equals(doc_password) && !properties.containsKey("credentials") ) {
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString("pf_username", (String) properties.get("login"));
                        editor.putString("pf_password", (String) properties.get("password"));
                        editor.putString("pf_usercouchId", (String) properties.get("_id"));
                        editor.putString("pf_userfirstname", (String) properties.get("firstName"));
                        editor.putString("pf_userlastname", (String) properties.get("lastName"));
                        editor.putString("pf_usergender", (String) properties.get("Gender"));
                        editor.putString("pf_lastVisitDate", doc_lastVisit);
                        try {
                            String noOfVisits = properties.get("visits").toString();
                            editor.putInt("pf_uservisits_Int", (Integer.parseInt(noOfVisits) + totalVisits((String) properties.get("_id"))));
                        } catch (Exception err) {
                        }
                        Set<String> stgSet = settings.getStringSet("pf_userroles", new HashSet<String>());
                        ArrayList roleList = (ArrayList<String>) properties.get("roles");
                        for (int cnt = 0; cnt < roleList.size(); cnt++) {
                            stgSet.add(String.valueOf(roleList.get(cnt)));
                        }
                        editor.putStringSet("pf_userroles", stgSet);
                        editor.commit();
                        Log.e("MYAPP", " RowChipsView Login OLD encryption: " + doc_loginId + " Password: " + doc_password);
                        Intent intent = new Intent(this, FullscreenActivity.class);
                        startActivity(intent);
                        return true;

                    }else if (doc_password == "" && !mPasswordView.getText().toString().equals("")) {
                        try {
                            Map<String, Object> doc_credentials = (Map<String, Object>) properties.get("credentials");
                            AndroidDecrypter adc = new AndroidDecrypter();
                            if(adc.AndroidDecrypter(doc_loginId, mPasswordView.getText().toString(), doc_credentials.get("value").toString())){
                                SharedPreferences.Editor editor = settings.edit();
                                editor.putString("pf_username", (String) properties.get("login"));
                                editor.putString("pf_password", (String) properties.get("password"));
                                editor.putString("pf_usercouchId", (String) properties.get("_id"));
                                editor.putString("pf_userfirstname", (String) properties.get("firstName"));
                                editor.putString("pf_userlastname", (String) properties.get("lastName"));
                                editor.putString("pf_usergender", (String) properties.get("Gender"));
                                editor.putString("pf_lastVisitDate", doc_lastVisit);
                                try {
                                    String noOfVisits = properties.get("visits").toString();
                                    editor.putInt("pf_uservisits_Int", (Integer.parseInt(noOfVisits) + totalVisits((String) properties.get("_id"))));
                                } catch (Exception err) {

                                }
                                Set<String> stgSet = settings.getStringSet("pf_userroles", new HashSet<String>());
                                ArrayList roleList = (ArrayList<String>) properties.get("roles");
                                for (int cnt = 0; cnt < roleList.size(); cnt++) {
                                    stgSet.add(String.valueOf(roleList.get(cnt)));
                                }
                                editor.putStringSet("pf_userroles", stgSet);
                                editor.commit();
                                Log.e("MYAPP", " RowChipsView Login Id: " + doc_loginId + " Password: " + doc_password);
                                Intent intent = new Intent(this, FullscreenActivity.class);
                                startActivity(intent);
                                return true;

                            }

                            ////doc_credentials.get("salt").toString());
                            ///doc_credentials.get("value").toString()
                        } catch (Exception err) {
                            Log.e("MYAPP", " Encryption Err  " + err.getMessage());
                        }

                    } else{
                        return false;
                    }
                }

            }
            db.close();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int totalVisits(String memberId){
        AndroidContext androidContext = new AndroidContext(this);
        Manager manager = null;
        Database visitHolder;
        int doc_noOfVisits;


        try {
            manager = new Manager(androidContext, Manager.DEFAULT_OPTIONS);
            visitHolder = manager.getDatabase("visits");
            Document retrievedDocument = visitHolder.getExistingDocument(memberId);
            if(retrievedDocument != null) {
                Map<String, Object> properties = retrievedDocument.getProperties();
                if(properties.containsKey("noOfVisits")){
                    doc_noOfVisits = (int) properties.get("noOfVisits") ;
                    doc_lastVisit = (String) properties.get("lastVisits");
                    /// Increase No Of visits by 1
                    Map<String, Object> newProperties = new HashMap<String, Object>();
                    newProperties.putAll(retrievedDocument.getProperties());
                    doc_noOfVisits += 1;
                    newProperties.put("noOfVisits", doc_noOfVisits);
                    newProperties.put("lastVisits", todaysDate());
                    retrievedDocument.putProperties(newProperties);
                    return doc_noOfVisits;
                }
            }
            else{
                Document newdocument = visitHolder.getDocument(memberId);
                Map<String, Object> newProperties = new HashMap<String, Object>();
                newProperties.put("noOfVisits", 1);
                doc_lastVisit = todaysDate();
                newProperties.put("lastVisits", doc_lastVisit);
                newdocument.putProperties(newProperties);
                return 1;
            }


        }catch(Exception err){
            Log.e("VISITS", "ERR : " +err.getMessage());

        }

        return 0;

    }

    public String todaysDate(){
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        System.out.println(dateFormat.format(cal.getTime()));
        return dateFormat.format(cal.getTime());

    }

    public void getSyncURLDialog(){
        AlertDialog.Builder dialogB = new AlertDialog.Builder(this);
        // custom dialog
        dialogB.setView(R.layout.dialog_setup);
        dialogB.setCancelable(true);
        dialog = dialogB.create();
        dialog.show();

        ///dialogDetails = dialogbuilder.create();

        final EditText txtSuncURL = (EditText) dialog.findViewById(R.id.txtNewSyncURL);
        txtSuncURL.setText(sys_oldSyncServerURL);
        Button dialogButton = (Button) dialog.findViewById(R.id.btnNewSaveSyncURL);
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sys_oldSyncServerURL = txtSuncURL.getText().toString();

                SharedPreferences.Editor editor = settings.edit();
                editor.putString("pf_sysncUrl", sys_oldSyncServerURL);
                editor.commit();

                dialog.dismiss();
                //Intent intent = new Intent(context,NewSync.class);
                //startActivity(intent);
                ///dialog.getOwnerActivity().setVisible(false);
                ///initSyncLoginDetails();
                mDialog = new ProgressDialog(context);
                mDialog.setMessage("Please wait...");
                mDialog.setCancelable(false);


                try {
                    syncNotifier();
                    //initDB();
                    //startSync();
                } catch (Exception e) {
                    e.printStackTrace();
                }


                mDialog.show();
            }
        });

    }
/*
    protected void initDB() throws IOException, CouchbaseLiteException {
        // create the database manager with default options
        Manager manager = new Manager(new AndroidContext(FullscreenLogin.this), Manager.DEFAULT_OPTIONS);

        // get or create the database with the provided name
         database = manager.getDatabase("members");

        // add a change listener
        //database.addChangeListener(databaseListener);
    }

    //start bi-directional syncing
    protected void startSync() {

        URL syncUrl;
        try {
            //syncUrl = new URL(sys_oldSyncServerURL+"/"+"members");
            syncUrl = new URL("http://10.10.207.152:5988/members");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // server - client
        pullReplication = database.createPullReplication(syncUrl);
        pullReplication.setContinuous(true);

        pullReplication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                if(pullReplication.isRunning()){
                    Log.e("MyCouch", " Members "+event.getChangeCount());
                    //message = String.valueOf(event.getChangeCount());
                }else {
                    Log.e("Finished", "DB count"+ database.getDocumentCount()+"");
                    if(syncCnt < (1)){
                        syncCnt++;
                        ///new NewSync.TestAsyncPull().execute();
                    }else{
                        Log.e("MyCouch","Sync Completed");
                        if(!openMemberList) {
                            openMemberList = true;
                        }
                        //triggerMemberResourceDownload();
                        ///synchronizingPull=true;
                    }

                }
            }
        });

        // replication listeners
        //pullReplication.addChangeListener(pullReplicationListener);

        // start both replications
        pullReplication.start();

    }


*/

    public void restorePref(){
        // Restore preferences
        settings = getSharedPreferences(PREFS_NAME, 0);
        sys_username = settings.getString("pf_username","");
        sys_oldSyncServerURL = settings.getString("pf_sysncUrl","");
        sys_lastSyncDate = settings.getString("pf_lastSyncDate","");
        sys_password = settings.getString("pf_password","");
        sys_usercouchId = settings.getString("pf_usercouchId","");
        sys_userfirstname = settings.getString("pf_userfirstname","");
        sys_userlastname = settings.getString("pf_userlastname","");
        sys_usergender = settings.getString("pf_usergender","");
        sys_uservisits = settings.getString("pf_uservisits","");

        if(sys_username!=""){
            mUsername.setText(sys_username);
        }else{
            mUsername.setText("");
        }
        Set<String>  mwr = settings.getStringSet("membersWithResource",null);
        try{
            sys_membersWithResource = mwr.toArray();
            Log.e("MYAPP", " membersWithResource  = "+sys_membersWithResource.length);

        }catch(Exception err){
            Log.e("MYAPP", " Error creating  sys_membersWithResource");
        }
    }

    public void syncNotifier(){
        final AsyncTask<Void, Integer, String> execute = new FullscreenLogin.TestAsyncPull().execute();
        Log.e("MyCouch", "syncNotifier Running");
        final Thread th = new Thread(new Runnable() {
            private long startTime = System.currentTimeMillis();
            public void run() {
                while (syncmembers) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(openMemberList) {
                                mDialog.dismiss();
                                ////triggerMemberResourceDownload();
                                openMemberList=false;
                            }
                            Log.d("runOnUiThread", "running");
                            mDialog.setMessage("Downloading, please wait .... " + (syncCnt + 1));
                        }
                    });
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        th.start();
    }

    class TestAsyncPull extends AsyncTask<Void, Integer, String> {
        protected void onPreExecute (){
            Log.d("PreExceute","On pre Exceute......");
        }

        protected String doInBackground(Void...arg0) {
            Log.d("DoINBackGround","On doInBackground...");
            syncmembers =true;
            pull= new Replication[databaseList.length];
            db = new Database[databaseList.length];
            manager = new Manager[databaseList.length];
            try {
                manager[syncCnt] = new Manager(androidContext, Manager.DEFAULT_OPTIONS);
                // if(wipeClearn){
                //try{
                //   db[syncCnt] = manager[syncCnt].getExistingDatabase(databaseList[syncCnt]);
                //    db[syncCnt].delete();
                // }catch(Exception err){
                //      Log.e("MyCouch", "Delete Error "+ err.getLocalizedMessage());
                //   }
                //}

                db[syncCnt] = manager[syncCnt].getDatabase(databaseList[syncCnt]);
                URL url = new URL(sys_oldSyncServerURL+"/"+databaseList[syncCnt]);
                pull[syncCnt]=  db[syncCnt].createPullReplication(url);
                pull[syncCnt].setContinuous(false);
                pull[syncCnt].addChangeListener(new Replication.ChangeListener() {
                    @Override
                    public void changed(Replication.ChangeEvent event) {
                        if(pull[syncCnt] .isRunning()){
                            Log.e("MyCouch", databaseList[syncCnt]+" "+event.getChangeCount());
                            //message = String.valueOf(event.getChangeCount());
                        }else {
                            Log.e("Finished", databaseList[syncCnt]+" "+ db[syncCnt].getDocumentCount());
                            if(syncCnt < (databaseList.length-2)){
                                syncCnt++;
                                new FullscreenLogin.TestAsyncPull().execute();
                            }else{
                                Log.e("MyCouch","Sync Completed");
                                if(!openMemberList) {
                                    openMemberList = true;
                                }
                            }

                        }
                    }
                });
                pull[syncCnt].start();

            } catch (Exception e) {
                Log.e("MyCouch", databaseList[syncCnt]+" "+" Cannot create database", e);


            }
            publishProgress(syncCnt);
            return "You are at PostExecute";
        }
        protected void onProgressUpdate(Integer...a){
            Log.d("onProgress","You are in progress update ... " + a[0]);
            if(syncCnt != (databaseList.length-2)){
                //tv.setText(tv.getText().toString()+" \n Pulled "+databaseList[syncCnt]+ "\n Pulling "+ databaseList[syncCnt+1]+"....." );
                //tv.scrollTo(0,(tv.getLineCount()*20+syncCnt));
                //tv.requestFocus();
            }else{
                //tv.setText(tv.getText().toString()+" \n Pulled "+ databaseList[syncCnt] );
                //tv.scrollTo(0,(tv.getLineCount()*20)+syncCnt);
                //tv.requestFocus();
                //mDialog.setMessage(databaseList[syncCnt+1]+"Count");
            }
        }

        protected void onPostExecute(String result) {
            Log.d("OnPostExec",""+result);
        }
    }



}
