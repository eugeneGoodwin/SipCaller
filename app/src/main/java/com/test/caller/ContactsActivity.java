package com.test.caller;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class ContactsActivity extends Activity{

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;
    public static final int REQUEST_GET_CONTACTS = 33;

    private List<ContactData> mContactList = new ArrayList<>();
    private RecyclerView mRecyclerView;
    private ContactsAdapter mAdapter;

    private ContactData mSelectedContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view_contacts);

        mAdapter = new ContactsAdapter();
        mAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<ContactData>() {
            @Override
            public void onClick(ContactData contact) {
                mSelectedContact = contact;
                if(mSelectedContact != null)
                    returnData(mSelectedContact);
            }
        });
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);

        showContacts();
    }

    private void fillContacts(ContentResolver cr) {
        Cursor phones = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,null,null, null);
        // use the cursor to access the contacts
        while (phones.moveToNext())
        {
            String name=phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String phoneNumber = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            ContactData contact = new ContactData(name, phoneNumber);
            mContactList.add(contact);
        }
        mAdapter.setDisplayContacts(mContactList);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Show the contacts in the ListView.
     */
    private void showContacts() {
        // Check the SDK version and whether the permission is already granted or not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
        } else {
            // Android version is lesser than 6.0 or the permission is already granted.
            fillContacts(this.getContentResolver());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                fillContacts(this.getContentResolver());
            } else {
                Toast.makeText(this, "Until you grant the permission, we canot display the names", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void returnData(ContactData data) {
        Intent returnIntent = new Intent();
        if(data != null) {
            returnIntent.putExtra("Name", data.getName());
            returnIntent.putExtra("Phone", data.getPhone());
            setResult(RESULT_OK, returnIntent);
        }
        else
            setResult(RESULT_CANCELED, returnIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if(mSelectedContact != null)
            returnData(mSelectedContact);
        else
            returnData(null);
    }

    public static Intent launcher(Context context) {
        return new Intent(context, ContactsActivity.class);
    }

    public static void start(Activity context) {
        Intent intent = launcher(context);
        context.startActivityForResult(intent, ContactsActivity.REQUEST_GET_CONTACTS);
    }
}
