package ch.niceideas.eskimo.egmi.zookeeper;

public interface ElectionCallback {

    void onMasterChanged (String masterHostname);

    void onMasterGained ();
}
