package org.polito.hsm;

import com.fazecast.jSerialComm.SerialPort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HardwareDetector {

    public static SerialPort detectHardware() {
        SerialPort r = null;
        List<Callable<SerialPort>> callables = new ArrayList<>();
        SerialPort[] ports = SerialPort.getCommPorts();
        for(SerialPort p: ports) {
            callables.add(new CallableDetector(p));
        }
        // Create an ExecutorService
        ExecutorService executorService = Executors.newFixedThreadPool(ports.length);

        try {

            r = executorService.invokeAny(callables);
            System.out.println("First non-null result: " + r);
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            // Shutdown the executor service
            executorService.shutdown();
            return r;
        }

    }
}