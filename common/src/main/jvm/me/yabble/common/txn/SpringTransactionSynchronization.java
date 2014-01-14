package me.yabble.common.txn;

import com.google.common.base.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

public class SpringTransactionSynchronization extends TransactionSynchronizationAdapter {
    private static final Logger log = LoggerFactory.getLogger(SpringTransactionSynchronization.class);

    private static final String RESOURCE_NAME = "txn-sync-functions";

    //@Override
    //public void beforeCommit(boolean isReadOnly) {
    //}

    @Override
    public void afterCommit() {
        List<Function<Void, Void>> syncFns = (List<Function<Void, Void>>)
                TransactionSynchronizationManager.getResource(RESOURCE_NAME);
        if (syncFns != null) {
            for (Function<Void, Void> f : syncFns) {
                try {
                    f.apply(null);
                } catch (Exception e) {
                    log.error("Caught exception running after-commit function [{}]", e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void afterCompletion(int status) {
        if (TransactionSynchronizationManager.getResource(RESOURCE_NAME) != null) {
            TransactionSynchronizationManager.unbindResource(RESOURCE_NAME);
        }
    }

    public void add(Function<Void, Void> f) {
        List<Function<Void, Void>> fns = (List<Function<Void, Void>>)
                TransactionSynchronizationManager.getResource(RESOURCE_NAME);
        if (fns == null) {
            init();
            fns = (List<Function<Void, Void>>)
                    TransactionSynchronizationManager.getResource(RESOURCE_NAME);
        }
        fns.add(f);
    }

    private void init() {
        TransactionSynchronizationManager.registerSynchronization(this);

        TransactionSynchronizationManager.bindResource(
                RESOURCE_NAME, new ArrayList<Function<Void, Void>>());
    }
}
