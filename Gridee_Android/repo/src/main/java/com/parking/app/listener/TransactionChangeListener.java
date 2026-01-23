package com.parking.app.listener;

import com.parking.app.model.Transactions;
import com.parking.app.model.Wallet;
import com.parking.app.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;

import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

@Component
public class TransactionChangeListener {

    @Autowired
    private WalletRepository walletRepository;

    // This method listens to MongoDB after-save events for Transaction entities
    @EventListener
    public void handleTransactionChange(AfterSaveEvent<?> event) {
        Object source = event.getSource();

        if (!(source instanceof Transactions)) {
            // Ignore events not related to Transactions
            return;
        }

        Transactions tx = (Transactions) source;

        // Only act on completed transactions
        if (!"completed".equalsIgnoreCase(tx.getStatus()) || tx.getUserId() == null) return;

        Optional<Wallet> walletOpt = walletRepository.findByUserId(tx.getUserId());
        Wallet wallet = walletOpt.orElseGet(() -> {
            Wallet w = new Wallet();
            w.setUserId(tx.getUserId());
            w.setBalance(0);
            w.setLastUpdated(new Date());
            w.setTransactions(new ArrayList<>());
            return w;
        });

        double updatedBalance = wallet.getBalance();
        // Add to balance for topup/refund, subtract for payment
        if ("wallet_topup".equalsIgnoreCase(tx.getType()) || "refund".equalsIgnoreCase(tx.getType()))
            updatedBalance += tx.getAmount();
        else if ("payment".equalsIgnoreCase(tx.getType()))
            updatedBalance -= tx.getAmount();

        wallet.setBalance(updatedBalance);
        wallet.setLastUpdated(new Date());

        // Add TransactionRef
        Wallet.TransactionRef ref = new Wallet.TransactionRef();
        ref.setReferenceId(tx.getReferenceId());
        ref.setType(tx.getType());
        ref.setAmount(tx.getAmount());
        ref.setStatus(tx.getStatus());

        ArrayList<Wallet.TransactionRef> txnList = wallet.getTransactions() == null
                ? new ArrayList<>()
                : new ArrayList<>(wallet.getTransactions());
        txnList.add(ref);
        wallet.setTransactions(txnList);

        walletRepository.save(wallet);
    }
}
