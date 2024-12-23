package util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class PortUtil {

    private final String serverType;
    private final File configFile = new File("../config.txt");

    public PortUtil(String serverType){
        this.serverType = serverType;
    }
    private int generateRandomPort() {
        return 10000 + new Random().nextInt(55535);
    }

    public int getAvailableLocalStoragePort() {
        while (true) {
            int randomPort = generateRandomPort();
            try (RandomAccessFile raf = new RandomAccessFile(configFile, "rw")) {
                FileChannel fileChannel = raf.getChannel();
                FileLock lock = fileChannel.lock();
                String line;
                boolean isPortUsed = false;
                while ((line = raf.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 3) {
                        int port = Integer.parseInt(parts[1].trim());
                        if (port == randomPort) {
                            isPortUsed = true;
                            break;
                        }
                    }
                }
                if (!isPortUsed) {
                    List<PortConfigType> allPortState = loadPortStatus(raf);
                    allPortState.add(new PortConfigType("TCP", randomPort, "LocalStorage"));
                    savePortStatus(raf, allPortState);
                    return randomPort;
                }

            } catch (IOException e) {
                System.out.println("Error accessing config file - " + e.getMessage());
            }
        }

    }

    public boolean isAvailablePort(int port){

        try (RandomAccessFile raf = new RandomAccessFile(configFile, "rw")) {
            FileChannel fileChannel = raf.getChannel();
            FileLock lock = fileChannel.lock();

            List<PortConfigType> allPortState = loadPortStatus(raf);
            for (PortConfigType state : allPortState) {
                int existPort = state.port();
                if (existPort == port) {
                    return false;
                }
            }
            allPortState.add(new PortConfigType("TCP", port, serverType));
            savePortStatus(raf, allPortState);

        } catch (IOException e) {
            System.out.println("Error accessing config file - " + e.getMessage());
            return false;
        }
        return true;
    }
    private List<PortConfigType> loadPortStatus(RandomAccessFile raf) throws IOException {
        List<PortConfigType> configTypeList = new ArrayList<>();
        String line;
        raf.seek(0);
        while ((line = raf.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts.length >= 3) {
                String existingProtocol = parts[0].trim();
                int existingPort = Integer.parseInt(parts[1].trim());
                String serverType = parts[2].trim();
                configTypeList.add(new PortConfigType(existingProtocol, existingPort, serverType));
            }
        }
        return configTypeList;
    }
    private void savePortStatus(RandomAccessFile raf, List<PortConfigType> configList) throws IOException{
        raf.setLength(0);
        for (PortConfigType config : configList) {
            String line = config.protocol() + ":" + config.port() + ":" + config.serverType() + System.lineSeparator();
            raf.writeBytes(line);
        }
    }
    public void deletePortStatus(String protocol, int port) {
        try (RandomAccessFile raf = new RandomAccessFile(configFile, "rw")) {
            FileChannel fileChannel = raf.getChannel();
            FileLock lock = fileChannel.lock();

            List<PortConfigType> allPortState = loadPortStatus(raf);
            Iterator<PortConfigType> iterator = allPortState.iterator();
            while (iterator.hasNext()) {
                PortConfigType state = iterator.next();
                if (state.protocol().equals(protocol) && state.port() == port) {
                    iterator.remove();
                    break;
                }
            }
            savePortStatus(raf, allPortState);
        } catch (IOException e) {
            System.out.println("Error accessing config file - " + e.getMessage());
        }
    }
}


