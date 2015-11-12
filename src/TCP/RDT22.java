package TCP;
import java.io.IOException;

/**
 * Implements simulator using rdt2.2 protocol
 * 
 * @author rms
 *
 */
public class RDT22 extends RTDBase {

    /**
     * Constructs an RDT22 simulator with given munge factor
     * @param pmunge		probability of character errors
     * @throws IOException	if channel transmissions fail
     */
    public RDT22(double pmunge) throws IOException {this(pmunge, 0.0, null);}

    /**
     * Constructs an RDT22 simulator with given munge factor, loss factor and file feed
     * @param pmunge		probability of character errors
     * @param plost			probability of packet loss
     * @param filename		file used for automatic data feed
     * @throws IOException	if channel transmissions fail
     */
    public RDT22(double pmunge, double plost, String filename) throws IOException {
        super(pmunge, plost, filename);
        sender = new RSender22();
        receiver = new RReceiver22();
    }

    /**
     * Packet appropriate for rdt2.2;
     * contains data, seqnum and checksum
     * @author rms
     *
     */
    public static class Packet extends RDT21.Packet {
        public Packet(String data){
            super(data);
        }
        public Packet(String data, String seqnum){
            super(data, seqnum);
        }
        public Packet(String data, String seqnum, String checksum) {
            super(data, seqnum, checksum);
        }
        public static Packet deserialize(String data) {
            String hex = data.substring(0, 4);
            String seqnum = data.substring(4,5);
            String dat = data.substring(5);
            return new Packet(dat, seqnum, hex);
        }
        public int ackNum() {
            if (!data.equals("ACK")) {
                return -1;
            }
            if (seqnum.equals("0")) {
                return 0;
            }
            return 1;
        }
    }

    /**
     * RSender Class implementing rdt2.2 protocol
     * @author rms
     *
     */
    public class RSender22 extends RSender {
        Packet packet = null;
        @Override
            public int loop(int myState) throws IOException {
                Packet pack = null;
                String acknak = null;
                String dat = null;
                switch(myState) {
                    case 0:
                        dat = getFromApp(0);
                        packet = new Packet(dat, "0");
                        System.out.printf("Sender(0): %s\n", packet.toString());
                        System.out.println("  **Sender(0->1):");
                        forward.send(packet);
                        return 1;
                    case 1:
                        acknak = backward.receive();
                        pack = Packet.deserialize(acknak);
                        System.out.printf("  **Sender(1): %s ***\n", pack.toString());
                        if (pack.ackNum() == -1 || pack.ackNum() == 1 || pack.isCorrupt()) {
                            System.out.println("  **Sender(1->1): wrong or corrupt acknowledgement; resending ***");
                            forward.send(packet);
                            return 1;
                        }
                        System.out.println("  **Sender(1->2)");
                        return 2;
                    case 2:
                        dat = getFromApp(0);
                        packet = new Packet(dat, "1");
                        System.out.printf("Sender(2): %s\n", packet.toString());
                        System.out.println("  **Sender(2->3):");
                        forward.send(packet);
                        return 3;
                    case 3:
                        acknak = backward.receive();
                        pack = Packet.deserialize(acknak);
                        System.out.printf("  **Sender(3): %s ***\n", pack.toString());
                        if (pack.ackNum() == -1 || pack.ackNum() == 0 || pack.isCorrupt()) {
                            System.out.println("  **Sender(3->3): wrong or corrupt acknowledgement; resending ***");
                            forward.send(packet);
                            return 3;
                        }
                        System.out.println("  **Sender(3->0)");
                        return 0;
                }
                return myState;
            }
    }

    /**
     * RReceiver Class implementing rdt2.2 protocol
     * @author rms
     *
     */
    public class RReceiver22 extends RReceiver {
        @Override
            public int loop(int myState) throws IOException {
                String dat = null;
                Packet packet = null;
                Packet p = null;
                switch (myState) {
                    case 0:
                        dat = forward.receive();
                        packet = Packet.deserialize(dat);
                        System.out.printf("         **Receiver(0): %s ***\n", packet.toString());
                        if (packet.isCorrupt()){
                        	System.out.println("         **Receiver(0->0): corrupt data; replying ACK/1 **");
                        	p = new Packet("ACK", "1");
                        	backward.send(p);
                            return 0;
                        }
                        if (packet.seqnum.equals("1")) {
                            System.out.println("         **Receiver(0->0): Duplicate 1 packet; discarding; replying ACK/1 **");
                            p = new Packet("ACK", "1");
                            backward.send(p);
                            return 0;
                        }
                        System.out.println("         **Receiver(0->1): ok 0 data; replying ACK/0 **");
                        deliverToApp(packet.data);
                        p = new Packet("ACK", "0");
                        backward.send(p);
                        return 1;
                    case 1:
                        dat = forward.receive();
                        packet = Packet.deserialize(dat);
                        System.out.printf("         **Receiver(1): %s ***\n", packet.toString());
                        if (packet.isCorrupt()){
                        	System.out.println("         **Receiver(1->1): corrupt data; replying ACK/0 **");
                        	p = new Packet("ACK", "0");
                        	backward.send(p);
                            return 1;
                        }
                        if (packet.seqnum.equals("0")) {
                            System.out.println("         **Receiver(1->1): Duplicate 0 packet; discarding; replying ACK/0 **");
                            p = new Packet("ACK", "0");
                            backward.send(p);
                            return 1;
                        }
                        System.out.println("         **Receiver(1->0): ok 1 data; replying ACK/1 **");
                        deliverToApp(packet.data);
                        p = new Packet("ACK", "1");
                        backward.send(p);
                        return 0;
                }
                return myState;
            }
    }


    /**
     * Runs rdt2.2 simulation
     * @param args	[-m pmunge][-l ploss][-f filename]
     * @throws IOException	if i/o error occurs
     */
    public static void main(String[] args) throws IOException {
        Object[] pargs = argParser("RDT22", args);
        RDT22 rdt22 = new RDT22((Double)pargs[0], (Double)pargs[1], (String)pargs[3]);
        rdt22.run();
    }

}
