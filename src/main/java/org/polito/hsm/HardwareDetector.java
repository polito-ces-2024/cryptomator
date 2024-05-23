package org.polito.hsm;

import com.fazecast.jSerialComm.SerialPort;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
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

    public static byte[] sendCommand(SerialPort comPort, byte command, byte[] payload) throws InterruptedException {

        int keySize = 32;
        comPort.openPort();
        comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);
        // Set serial port parameters
        comPort.setBaudRate(115200);
        comPort.setNumDataBits(8);
        comPort.setNumStopBits(1);
        comPort.setParity(SerialPort.NO_PARITY);

        /*Generate random 2^(8*9) key number*/
        //byte[] b = new byte[9];
        //SecureRandom.getInstanceStrong().nextBytes(b);
        byte[] toSend = ArrayUtils.add(payload, 0, command);

        System.out.println(Arrays.toString(toSend));

        comPort.writeBytes(toSend, toSend.length);
        while (comPort.bytesAvailable() == 0) Thread.sleep(20);

        byte[] readBuffer = new byte[keySize];
        int numRead = comPort.readBytes(readBuffer, readBuffer.length);
        comPort.closePort();
        System.out.println(Arrays.toString(readBuffer));
        return readBuffer;
    }
}