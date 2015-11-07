package TCP;
import java.io.IOException;

/**
 * Implements simulator using rdt2.0 protocol
 *
 * @author rms
 *
 */
public class RDT20 extends RTDBase {

    /**
     * Constructs an RDT20 simulator with given munge factor
     * @param pmunge		probability of character errors
     * @throws IOException	if channel transmissions fail
     */
    public RDT20(double pmunge) throws IOException {this(pmunge, 0.0, null);}

    /**
     * Constructs an RDT20 simulator with given munge factor, loss factor and file feed
     * @param pmunge		probability of character errors
     * @param plost			probability of packet loss
     * @param filename		file used for automatic data feed
     * @throws IOException	if channel transmissions fail
     */
    public RDT20(double pmunge, double plost, String filename) throws IOException {
        super(pmunge, plost, filename);
        sender = new RSender20();
        receiver = new RReceiver20();
    }

    /**
     * Packet appropriate for rdt2.0;
     * contains data and checksum
     * @author rms
     *
     */
    public static class Packet extends RDT10.Packet {
        public Packet(String data){
            super(data);
        }
        public Packet(String data, String checksum) {
            super(data, checksum);
        }
        public static Packet deserialize(String data) {
            String hex = data.substring(0, 4);
            String dat = data.substring(4);
            return new Packet(dat, hex);
        }
        public boolean isAck() {
            return data.equals("ACK");
        }
    }

    /**
     * RSender Class implementing rdt2.0 protocol
     * @author rms
     *
     */
    public class RSender20 extends RSender {
        Packet packet = null;
        @Override
            public int loop(int myState) throws IOException {
                switch(myState) {
                    case 0:
                        String dat = getFromApp(0);
                        packet = new Packet(dat);
                        System.out.printf("Sender(0): %s\n", packet.toString());
                        System.out.println("  **Sender(0->1):");
                        forward.send(packet);
                        return 1;
                    case 1:
                        String acknak = backward.receive();
                        Packet pack = Packet.deserialize(acknak);
                        System.out.printf("  **Sender(1): %s ***\n", pack.toString());
                        if (!pack.isAck()) {
                            System.out.println("  **Sender(1->1): NAK or corrupt acknowledgement; resending ***");
                            forward.send(packet);
                            return 1;
                        }
                        System.out.println("  **Sender(1->0)");
                        return 0;
                }
                return myState;
            }
    }

    /**
     * RReceiver Class implementing rdt2.0 protocol
     * @author rms
     *
     */
    public class RReceiver20 extends RReceiver {
        @Override
            public int loop(int myState) throws IOException {
                switch (myState) {
                    case 0:
                        String dat = forward.receive();
                        Packet packet = Packet.deserialize(dat);
                        System.out.printf("         **Receiver(0): %s ***\n", packet.toString());
                        if (packet.isCorrupt()){
                        	System.out.println("         **Receiver(0->0): corrupt data; replying NAK **");
                        	Packet p = new Packet("NAK");
                        	backward.send(p);
                        }
                        else {
                        	System.out.println("         **Receiver(0->0): ok data; replying ACK **");
							deliverToApp(packet.data);
                        	Packet p = new Packet("ACK");
                        	backward.send(p);
                        }
                        return 0;
                }
                return myState;
            }
    }

    /**
     * Runs rdt2.0 simulation
     * @param args	[-m pmunge][-l ploss][-f filename]
     * @throws IOException	if i/o error occurs
     */
    public static void main(String[] args) throws IOException {
        Object[] pargs = argParser("RDT20", args);
        RDT20 rdt20 = new RDT20((Double)pargs[0], (Double)pargs[1], (String)pargs[3]);
        rdt20.run();
    }

}
