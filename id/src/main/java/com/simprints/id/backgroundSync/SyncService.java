package com.simprints.id.backgroundSync;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.simprints.libdata.DatabaseContext;
import com.simprints.libdata.DatabaseEventListener;
import com.simprints.libdata.Event;
import com.simprints.libdata.models.M_ApiKey;

import java.util.List;

public class SyncService extends IntentService {
    public SyncService() {
        super("SyncService");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        DatabaseContext.initActiveAndroid(getApplicationContext());
        List<M_ApiKey> keys = DatabaseContext.getSyncKeys();

        for (final M_ApiKey key : keys) {
            new DatabaseContext(key.asString(), getApplicationContext(), new DatabaseEventListener() {
                @Override
                public void onDataEvent(Event event) {
                    Log.d("SyncService", event.toString());
                    Toast.makeText(getApplicationContext(), event.toString(), Toast.LENGTH_SHORT).show();
                }
            }).sync();
        }
    }
}
