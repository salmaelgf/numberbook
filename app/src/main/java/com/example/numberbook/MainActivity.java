package com.example.numberbook;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.numberbook.adapter.ContactAdapter;
import com.example.numberbook.beans.Contact;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_ALL = 100;

    private ApiService apiService;
    private RecyclerView recyclerView;
    private SearchView searchView;
    private ContactAdapter adapter;
    private List<Contact> contactsList = new ArrayList<>();
    private List<Contact> filteredContactsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Configuration de la Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        apiService = RetrofitClient.getApiService();
        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.searchView);

        // Configuration du RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactAdapter(this, filteredContactsList);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        // Configuration du FAB
        FloatingActionButton fabAdd = findViewById(R.id.fab_add);
        fabAdd.setOnClickListener(view -> {
            Toast.makeText(this, "Ajouter un nouveau contact", Toast.LENGTH_SHORT).show();
            setContentView(R.layout.dialog_add_contact);
        });

        // Configuration de la SearchView
        setupSearchView();

        // Vérification des permissions
        checkPermissionsAndLoadContacts();
    }


    private void checkPermissionsAndLoadContacts() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.READ_CONTACTS);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.CALL_PHONE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.SEND_SMS);

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_ALL);
        } else {
            // D'abord charger les contacts du téléphone
            loadPhoneContactsAndSync();
        }
    }

    private void loadPhoneContactsAndSync() {
        // Charger les contacts du téléphone
        List<Contact> phoneContacts = ContactFetcher.fetchPhoneContacts(getContentResolver());

        // Afficher immédiatement les contacts du téléphone
        contactsList.clear();
        contactsList.addAll(phoneContacts);
        filteredContactsList.clear();
        filteredContactsList.addAll(contactsList);
        adapter.updateContacts(filteredContactsList);

        Toast.makeText(this, "Contacts du téléphone chargés", Toast.LENGTH_SHORT).show();

        // Synchroniser les contacts avec le serveur
        syncContactsWithServer(phoneContacts);
    }

    private void syncContactsWithServer(List<Contact> phoneContacts) {
        // Envoyer tous les contacts du téléphone au serveur
        apiService.saveContactsBatch(phoneContacts).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this,
                            "Contacts synchronisés avec le serveur",
                            Toast.LENGTH_SHORT).show();

                    // Ensuite, récupérer tous les contacts du serveur
                    fetchContactsFromServer();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Erreur lors de la synchronisation: " + response.message(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this,
                        "Erreur réseau lors de la synchronisation: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchContactsFromServer() {
        apiService.getAllContacts().enqueue(new Callback<List<Contact>>() {
            @Override
            public void onResponse(Call<List<Contact>> call, retrofit2.Response<List<Contact>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    contactsList = response.body();
                    filteredContactsList.clear();
                    filteredContactsList.addAll(contactsList);
                    adapter.updateContacts(filteredContactsList);

                    Toast.makeText(MainActivity.this,
                            "Contacts mis à jour depuis le serveur",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Échec du chargement des contacts: " + response.message(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Contact>> call, Throwable t) {
                Toast.makeText(MainActivity.this,
                        "Erreur réseau lors du chargement des contacts: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterContacts(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterContacts(newText);
                return true;
            }
        });
    }

    private void filterContacts(String query) {
        if (query.isEmpty()) {
            filteredContactsList.clear();
            filteredContactsList.addAll(contactsList);
        } else {
            String queryLowerCase = query.toLowerCase();
            filteredContactsList.clear();
            filteredContactsList.addAll(contactsList.stream()
                    .filter(contact ->
                            contact.getName().toLowerCase().contains(queryLowerCase) ||
                                    contact.getNumber().contains(query))
                    .collect(Collectors.toList()));
        }
        adapter.updateContacts(filteredContactsList);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_ALL) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                loadPhoneContactsAndSync();
            } else {
                Toast.makeText(this, "Toutes les permissions sont requises pour utiliser l'application", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

}