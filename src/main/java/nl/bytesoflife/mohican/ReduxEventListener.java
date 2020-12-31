package nl.bytesoflife.mohican;

public interface ReduxEventListener {
    public void onMessage(ReduxAction action);
    public void onConnect();
    public void onDisconnect();
}
