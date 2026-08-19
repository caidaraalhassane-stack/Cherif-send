package com.cherif.send;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;
import com.cherif.send.transfer.LocalTransferPlugin;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LocalTransferPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
