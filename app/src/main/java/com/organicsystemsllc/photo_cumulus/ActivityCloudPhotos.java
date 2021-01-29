package com.organicsystemsllc.photo_cumulus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.organicsystemsllc.photo_cumulus.models.CloudPhoto;

import java.util.List;
import java.util.UUID;

public class ActivityCloudPhotos extends AppCompatActivity
        implements ChildEventListener, GoogleApiClient.OnConnectionFailedListener,
        AdapterCloudPhoto.ImageAdapterOnClickHandler {

    private static final int PICK_IMAGE = 100;
    private static final int ACTIVITY_SIGN_IN = 200;
    private static final String TAG = ActivityCloudPhotos.class.getSimpleName();
    private DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
    private FirebaseStorage mStorage = FirebaseStorage.getInstance();
    private String mUserId;
    private FirebaseAuth mAuth;
    private AdapterCloudPhoto mAdapterCloudPhoto;
    private ProgressBar mProgressBar;
    private StorageReference mStorageReference;
    private String mPath;
    private UploadTask mUploadTask;
    private Button mSigninButton;
    private TextView mErrorMessage;
    private GoogleApiClient mGoogleApiClient;
    private FloatingActionButton mFab;
    private Intent mPendingIntent;
    private FirebaseAnalytics mFirebaseAnalytics;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_links);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mProgressBar = findViewById(R.id.pb_loading_indicator);
        mSigninButton = findViewById(R.id.ib_google_signin);
        mErrorMessage = findViewById(R.id.tv_error_message_display);

        RecyclerView recyclerView = findViewById(R.id.rv_cloud_photo_list);
        DividerItemDecoration verticalDividerItemDecoration = new DividerItemDecoration(this, RecyclerView.VERTICAL);
        DividerItemDecoration horizontalDividerItemDecoration = new DividerItemDecoration(this, RecyclerView.HORIZONTAL);

        recyclerView.addItemDecoration(verticalDividerItemDecoration);
        recyclerView.addItemDecoration(horizontalDividerItemDecoration);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        //layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        layoutManager.setReverseLayout(false);
        recyclerView.setLayoutManager(layoutManager);


        mAdapterCloudPhoto = new AdapterCloudPhoto(this);
        recyclerView.setAdapter(mAdapterCloudPhoto);


        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                CloudPhoto cloudPhoto = (CloudPhoto) viewHolder.itemView.getTag();
                removeCloudPhoto(cloudPhoto);
                //mAdapter.swapCursor(getAllGuests());

            }
        }).attachToRecyclerView(recyclerView);



        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        mAuth = FirebaseAuth.getInstance();

        if (mUserId == null) {
            signInWithGoogle(null);
        }

        mFab = findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);

            }
        });


        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();


        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                mPendingIntent = intent; // Handle single image being sent
            }
        }


        AdView adView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest
                .Builder().addTestDevice("1A5A9BF76E9C3FBCC44BA658DDBF6389")
                .addTestDevice("7DAF8B0A35110313310C75F054F03511").build();
        adView.loadAd(adRequest);

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        FirebaseCrash.log("Activity created");

    }

    private void handleSendImage(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            // Update UI to reflect image being shared
            if (mUserId != null) {
                mPath = "user-photos/" + mUserId + "/" + UUID.randomUUID();
                mStorageReference = mStorage.getReference(mPath);
                mUploadTask = mStorageReference.putFile(imageUri);
                addStorageListeners();
            } else {
                Toast.makeText(this, R.string.toast_not_signed_in, Toast.LENGTH_SHORT).show();
            }

        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mStorageReference != null && mPath != null && mUserId != null) {
            outState.putString("storage_reference", mStorageReference.toString());
            outState.putString("storage_path", mPath);
            outState.putString("user_id", mUserId);

        }

        super.onSaveInstanceState(outState);

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mPath = savedInstanceState.getString("storage_path");
        String stringRef = savedInstanceState.getString("storage_reference");
        mUserId = savedInstanceState.getString("user_id");

        if (stringRef != null && mPath != null && mUserId != null) {

            mStorageReference = FirebaseStorage.getInstance().getReferenceFromUrl(stringRef);

            List<UploadTask> tasks = mStorageReference.getActiveUploadTasks();
            if (tasks.size() > 0) {
                mUploadTask = tasks.get(0);
                //addStorageListeners();
            }

        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case PICK_IMAGE:
                if (resultCode == RESULT_OK) {
                    //got an image

                    mProgressBar.setVisibility(View.VISIBLE);
                    Uri imageUri = data.getData();
                    if (imageUri != null) {

                        //upload image file
                        mPath = "user-photos/" + mUserId + "/" + UUID.randomUUID();
                        mStorageReference = mStorage.getReference(mPath);

                        try {
                            mUploadTask = mStorageReference.putFile(imageUri);
                            addStorageListeners();
                        } catch (SecurityException e) {
                            Log.e(TAG, e.getMessage());
                            FirebaseCrash.report(e);
                            Toast.makeText(this,
                                    R.string.toast_no_permission_to_uri, Toast.LENGTH_LONG).show();
                        }
                    }

                }
                break;
            case ACTIVITY_SIGN_IN:
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                if (result.isSuccess()) {
                    //updateUser(result.getSignInAccount());
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Google sign in success: " + result.toString());
                    }

                    // Google Sign In was successful, authenticate with Firebase
                    GoogleSignInAccount account = result.getSignInAccount();
                    firebaseAuthWithGoogle(account);
                } else {
                    // Google Sign In failed, update UI appropriately
                    updateUser(null);
                }
        }
    }

    @Override
    public void onChildAdded(DataSnapshot dataSnapshot, String url) {
        CloudPhoto cloudPhoto = dataSnapshot.getValue(CloudPhoto.class);
        if (cloudPhoto != null) {
            //Toast.makeText(this, "Image added to cloud DB.",
            //        Toast.LENGTH_SHORT).show();
            mAdapterCloudPhoto.addImageUrl(cloudPhoto);
        }
    }

    @Override
    public void onChildChanged(DataSnapshot dataSnapshot, String s) {

    }

    @Override
    public void onChildRemoved(DataSnapshot dataSnapshot) {
        CloudPhoto cloudPhoto = dataSnapshot.getValue(CloudPhoto.class);
        String key = dataSnapshot.getKey();
        if (cloudPhoto != null) {
            //Toast.makeText(this, "Image removed from cloud DB.",
            //        Toast.LENGTH_SHORT).show();
            mAdapterCloudPhoto.removeImageUrl(key);

            // Create a storage reference from our app
            StorageReference storageRef = mStorage.getReference();

            // Create a reference to the file to delete
            StorageReference desertRef = storageRef.child(cloudPhoto.photoPath);


            // Delete the file
            desertRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Toast.makeText(ActivityCloudPhotos.this, "Cloud image removed.",
                            Toast.LENGTH_SHORT).show();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    Log.e(TAG, exception.getMessage());
                }
            });


        }

    }

    @Override
    public void onChildMoved(DataSnapshot dataSnapshot, String s) {

    }

    @Override
    public void onCancelled(DatabaseError databaseError) {
        FirebaseCrash.log(databaseError.getMessage());
        FirebaseCrash.log(databaseError.getDetails());
        FirebaseCrash.logcat(Log.ERROR, TAG, databaseError.getMessage());
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        FirebaseCrash.log(connectionResult.getErrorMessage());
        FirebaseCrash.logcat(Log.ERROR, TAG, connectionResult.getErrorMessage());

    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {

        mProgressBar.setVisibility(View.VISIBLE);

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "signInWithCredential:success");
                            }
                            FirebaseUser currentUser = mAuth.getCurrentUser();

                            updateUser(currentUser);

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            updateUser(null);
                        }

                        mProgressBar.setVisibility(View.INVISIBLE);

                    }
                });
    }

    private void updateUser(FirebaseUser acct) {
        if (acct != null) {
            //mFirebaseAnalytics.setUserId(acct.getUid());
            mUserId = acct.getUid();

            if (mPendingIntent != null) {
                handleSendImage(mPendingIntent);
                mPendingIntent = null;
            }

            mFirebaseAnalytics.setUserId(mUserId);

            mDatabase.child("user-photos").child(mUserId).addChildEventListener(this);
            mSigninButton.setVisibility(View.INVISIBLE);
            mErrorMessage.setVisibility(View.INVISIBLE);
            mFab.setVisibility(View.VISIBLE);
        } else {
            mErrorMessage.setVisibility(View.VISIBLE);
            mSigninButton.setVisibility(View.VISIBLE);
            mFab.setVisibility(View.INVISIBLE);


        }
    }

    @Override
    public void onImageClick(CloudPhoto cloudPhoto) {
        String imageUrl = cloudPhoto.photoUrl;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(imageUrl));
        startActivity(intent);
    }

    private void removeCloudPhoto(CloudPhoto cloudPhoto) {

        DatabaseReference linkRef = mDatabase
                .child("user-photos")
                .child(mUserId)
                .child(cloudPhoto.databaseKey);

        linkRef.removeValue();
    }

    private void addStorageListeners() {

        //add listener
        mUploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Uri url = taskSnapshot.getDownloadUrl();
                        if (url != null) {
                            Toast.makeText(ActivityCloudPhotos.this,
                                    "Image uploaded to cloud.", Toast.LENGTH_SHORT)
                                    .show();

                            if (mUserId == null) {
                                FirebaseCrash.log("Null user ID in OnSuccess listener, may have stranded image url!");
                                return;
                            }


                            DatabaseReference linkRef = mDatabase.child("user-photos").child(mUserId).push();
                            CloudPhoto cloudPhoto = new CloudPhoto(linkRef.getKey(), url.toString(), mPath);
                            linkRef.setValue(cloudPhoto);
                            mProgressBar.setVisibility(View.INVISIBLE);
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(ActivityCloudPhotos.this,
                                "Upload failed.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, e.getMessage());
                        FirebaseCrash.logcat(Log.ERROR, TAG, "Upload failed");
                        FirebaseCrash.report(e);
                    }
                }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                        int progress = (int)((100.0 * taskSnapshot.getBytesTransferred())
                            / taskSnapshot.getTotalByteCount());

                        Toast.makeText(ActivityCloudPhotos.this,
                                "Upload is " + progress + "% done", Toast.LENGTH_SHORT).show();
                    }
                });

    }


    public void signInWithGoogle(View view) {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, ACTIVITY_SIGN_IN);
    }
}
