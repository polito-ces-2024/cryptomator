package org.polito.hsm;

import com.fazecast.jSerialComm.SerialPort;

import java.util.concurrent.Callable;

public class CallableDetector  implements Callable<SerialPort> {
    private SerialPort portToCheck;
    public CallableDetector(SerialPort portToCheck) {
        this.portToCheck = portToCheck;
    }
    @Override
    public SerialPort call() throws Exception {
            portToCheck.openPort();
            portToCheck.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);
            // Set serial port parameters
            portToCheck.setBaudRate(115200);
            portToCheck.setNumDataBits(8);
            portToCheck.setNumStopBits(1);
            portToCheck.setParity(SerialPort.NO_PARITY);


            byte b[] = new byte[]{0};
            portToCheck.writeBytes(b, b.length);


            while (portToCheck.bytesAvailable() == 0)
                Thread.sleep(20);

            byte[] readBuffer = new byte[33];
            int numRead = portToCheck.readBytes(readBuffer, readBuffer.length);

            portToCheck.closePort();

            if (readBuffer[1] == 2 && readBuffer[32] == 1) {
                return portToCheck;
            } else {
                throw new Exception("No Com PORT");
            }
        }
    }

