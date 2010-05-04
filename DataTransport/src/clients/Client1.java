/*
   Copyright 2009 S. Koulouzis

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  
 */


package clients;

import com.lowagie.text.pdf.codec.Base64;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;



import java.rmi.RemoteException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;

import javax.xml.soap.MessageFactory;
import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import nl.uva.science.wsdtf.utilities.StreamConfig;
import org.apache.axis.client.async.AsyncCall;
import org.apache.axis.client.async.IAsyncResult;

/**
 *
 * @author S. Koulouzis 
 */
public class Client1 {

    public static final int SWA = 100;
    public static final int SOAP = 200;
    public static final int HTTP = 300;
    public static final int GFTP = 400;
    public static final int TCP = 500;

    /** Creates a new instance of Client1 */
    public Client1() {
    }

    public static void main(String args[]) {
        Client1 c = new Client1();
        Object[] arg = {new String("")};
        if (args[0].equals("attach")) {
            System.out.print("Responce: " + c.callWA(arg, "getAttachments", "http://" + args[1] + ":8080/axis/services/DataTransportService", args[2]));
        }
        if (args[0].equals("soap")) {
//            String[] arg2 = {"From","To"};
            try {
//            System.out.print("Responce: "+ c.callWithSoap(arg2,"FileTransport","http://"+args[1]+":8080/axis/services/",args[2]));
                c.createSoapMessage("getFilesOverSoap", "http://" + args[1] + ":8080/axis/services/DataTransportService", args[2]);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (args[0].equals("simpleCall")) {
            Object[] arg2 = {new String("From"), new String("To")};
            c.simleCall(arg2, "FileTransport", "http://" + args[1] + ":8080/axis/services/");
        }

        if (args[0].equals("serv")) {
            try {
                System.out.println("args[1] = " + args[1]);
                Object[] arg2 = {new String("From")};
                String dataRef = c.call(arg2, "listTomcatApplets", "http://" + args[1] + ":8080/axis/services/DataTransportService");
                URL url = new URL(dataRef);
                URLConnection connection = url.openConnection();
                connection.setDoOutput(true);

                connection = url.openConnection();
                connection.setDoOutput(true);
                OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
                out.write("dsc");
                out.close();
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                String decodedString;
                while ((decodedString = in.readLine()) != null) {
                    System.out.println(decodedString);
                }
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(Client1.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (args[0].equals("fileOverSoap")) {
            try {
                c.deleteFile("http://" + args[1] + ":8080/axis/services/", "/home/alogo/testTrans/client/" + new File(args[2]).getName());
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
            } catch (RemoteException ex) {
                ex.printStackTrace();
            } catch (ServiceException ex) {
                ex.printStackTrace();
            }
            System.out.println(c.fileOverSoap("http://" + args[1] + ":8080/axis/services/", args[2]));
        }

        if (args[0].equals("http")) {
            Object[] arg2 = {"/home/alogo/workspace/AIDA/pdfDir", "http://192.168.1.11:8080/axis/services/", "/home/alogo/testTrans/client", 400};
            System.out.println(c.call(arg2, "sendFile", "http://" + args[1] + ":8080/axis/services/DataTransportService"));
        }

        if (args[0].equals("tcp")) {
            try {
                Object[] arg2 = {"/home/alogo/workspace/AIDA/pdfDir"};
                System.out.println(c.asyncCall(arg2, "startTCPServer", "http://" + args[1] + ":8080/axis/services/"));


                Object[] arg3 = {""};
                String conf = c.call(arg3, "getTCPServerEndPoint", "http://" + args[1] + ":8080/axis/services/DataTransportService");
                System.out.println(conf);
//                Initilize init = new Initilize();
//                String conf = init.Config2String(c.getConf(new URI("tcp://"+args[2]),"TCP"));
                Object[] arg4 = {conf, "/home/alogo/testTrans/client"};
                System.out.println(c.call(arg4, "getFileOverTCP", "http://" + args[2] + ":8080/axis/services/DataTransportService"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (args[0].equals("simpleService")) {
            System.out.println("args[1] = " + args[1]);
            Object[] arg2 = {new Long(90000)};
            String dataRef = c.call(arg2, "factor", "http://" + args[1] + ":8080/axis/services/SimleService");
            System.out.println(dataRef);
        }

        if (args[0].equals("proxyCall")) {
            System.out.println("args[1] = " + args[1]);
            Object[] arg2 = {new Long(90000)};
            String dataRef = c.call(arg2, "proxyCall", "http://" + args[1] + ":8080/axis/services/DataTransportService");
            System.out.println(dataRef);
        }
    }

    private String asyncCall(Object[] args, String method, String endpoint) {
        Service service = null;
        Call call = null;
        AsyncCall aCall = null;
        String result = null;
        IAsyncResult asyncResult = null;
        try {
            service = new Service();
            call = (Call) service.createCall();
            call.setTargetEndpointAddress(new URL(endpoint + "DataTransportService"));
            call.setOperationName(new QName(method));

            aCall = new AsyncCall(call);

            asyncResult = aCall.invoke(args);
        } catch (ServiceException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
        return (String) asyncResult.getResponse();
    }

    private String call(Object[] args, String method, String endpoint) {
        Service service = null;
        Call call = null;
        String result = null;
        try {
            service = new Service();
            call = (Call) service.createCall();
            call.setTargetEndpointAddress(new URL(endpoint));
            call.setOperationName(new QName(method));
            result = (String) call.invoke(args);
        } catch (ServiceException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
        return result;
    }

    private String callWA(Object[] args, String method, String endpoint, String file) {
        Service service = null;
        Call call = null;
        String result = null;
        try {
            service = new Service();
            call = (Call) service.createCall();
            call.setTargetEndpointAddress(new URL(endpoint + "TestWS"));
            call.setOperationName(new QName(method));
            if (new File(file).isDirectory()) {
                DataHandler[] dh = getAttachmentsFromDir(file);
                for (int i = 0; i < dh.length; i++) {
                    AttachmentPart ap = new AttachmentPart(dh[i]);
                    call.addAttachmentPart(ap);
                }
            } else {
                DataHandler dh = new DataHandler(new FileDataSource(file));
                AttachmentPart ap = new AttachmentPart(dh);
                call.addAttachmentPart(ap);
            }
            result = (String) call.invoke(args);
        } catch (ServiceException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }

        return result;
    }

    private String simleCall(Object[] args, String method, String endpoint) {
        Service service = null;
        Call call = null;
        String result = null;
        try {
            service = new Service();
            call = (Call) service.createCall();
            call.setTargetEndpointAddress(new URL(endpoint));
            call.setOperationName(new QName(method));
            result = (String) call.invoke(args);
            try {

                System.out.println("Reuest:----------------------------");
                displayMessage(call.getMessageContext().getRequestMessage());


                System.out.println("--");
                System.out.println("Responce:-------------------------");
                displayMessage(call.getMessageContext().getResponseMessage());

            } catch (Exception ex) {
                ex.printStackTrace();
            }

        } catch (ServiceException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }

        return result;
    }

    private DataHandler[] getAttachmentsFromDir(String dirName) {
        java.util.LinkedList<DataHandler> retList = new java.util.LinkedList<DataHandler>();
        DataHandler[] ret = new DataHandler[0];// empty

        java.io.File sourceDir = new java.io.File(dirName);

        java.io.File[] files = sourceDir.listFiles();

        for (int i = files.length - 1; i >= 0; --i) {
            java.io.File cf = files[i];

            if (cf.isFile() && cf.canRead() && !cf.getName().equals(".")) {
                String fname = null;

                try {
                    fname = cf.getAbsoluteFile().getCanonicalPath();
                } catch (java.io.IOException e) {
                    System.err.println("Couldn't get file \"" + fname + "\" skipping...");
                    continue;
                }
                retList.add(new DataHandler(new FileDataSource(fname)));
            }
        }
        if (!retList.isEmpty()) {
            ret = new DataHandler[retList.size()];
            ret = (DataHandler[]) retList.toArray(ret);
        }

        return ret;
    }

    private String callWithSoap(Object[] args, String method, String endpoint, String file) {

        Service service = null;
        Call call = null;
        String result = null;
        try {
            service = new Service();
            call = (Call) service.createCall();
            call.setTargetEndpointAddress(new URL(endpoint + "TestWS"));

            try {
                this.displayMessage(call.getMessageContext().getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }

//            result = (String)call.invoke(args);
            result = (String) call.invoke(args);
//            call.getMessageContext().getMessage().getSOAPPart().setContent(new StreamSource(new FileInputStream(file)));
        } catch (ServiceException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
//        catch (FileNotFoundException ex) {
//            ex.printStackTrace();
//        } catch (SOAPException ex) {
//            ex.printStackTrace();
//        }

        return result;
    }

    public String fileOverSoap(String endpoint, String file) {
        Service service = null;
        Call call = null;
        String result = null;
        File f = new File(file);
        try {
            service = new Service();
            call = (Call) service.createCall();
            call.setTargetEndpointAddress(new URL(endpoint + "TestWS"));
            call.setOperationName(new QName("getFileOverSoap"));
            FileInputStream fileStream = new FileInputStream(file);
            InputStream in = fileStream;

            FileOutputStream fileOut = new FileOutputStream(file + ".tmp");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] tmp = new byte[5 * 1024 * 1024];
            int len = 0;
            String data = "";
            byte[] tmp2;
            while ((len = in.read(tmp)) != -1) {
//                data = Base64.encodeBytes(tmp,0,len);

                baos.write(tmp, 0, len);


//                System.out.println("Unencoded------------: "+new String(baos.toByteArray()));
                data = Base64.encodeBytes(baos.toByteArray());
//                System.out.println("Base64---------------: "+data);
                tmp2 = Base64.decode(data);
//                System.out.println("Dencoded-------------: " + new String(tmp2) );
//                System.out.println("Len------------------: " + len);

                fileOut.write(tmp2);

                Object[] args = {new String(f.getName()), data.getBytes()};
                result = (String) call.invoke(args);
                baos.reset();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    public SOAPMessage createSoapMessage(String method, String endpoint, String file) {

        SOAPMessage message = null;
        try {

            MessageFactory mf = MessageFactory.newInstance();
            message = mf.createMessage();

            SOAPPart soapPart = message.getSOAPPart();
            SOAPEnvelope envelope = soapPart.getEnvelope();
            envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
            envelope.getHeader().detachNode();

            SOAPBody body = envelope.getBody();
            SOAPElement bodyElement = body.addChildElement(method);

            SOAPElement arg0 = bodyElement.addChildElement("arg0");
            arg0.setAttribute("xsi:type", "SOAP-ENC:string");
            arg0.addTextNode("SomeFile");

            SOAPElement arg1 = bodyElement.addChildElement("arg1");
            arg1.setAttribute("xsi:type", "SOAP-ENC:string");
            arg1.addTextNode("/home/alogo/testTrans/client/");


            SOAPElement arg2 = bodyElement.addChildElement("arg2");
            arg2.setAttribute("xsi:type", "SOAP-ENC:base64Bin");
            String de = Base64.encodeBytes("HEEEEEEEEEEEEELOOOOOOOOOOOOOO".getBytes());
            arg2.setNodeValue("83698670828586708285867082858670828586778448578084485780844857808448578084485661");
//            arg2.addTextNode("83698670828586708285867082858670828586778448578084485780844857808448578084485661");     
//           "83698670828586708285867082858670828586778448578084485780844857808448578084485661"
//            "U0VWRlJVVkZSVVZGUlVWRlJVVk1UMDlQVDA5UFQwOVBUMDlQVDA4PQ=="
//            for(int i=0;i<de.getBytes().length;i++){
//                System.out.print(de.getBytes()[i]);
//            }

//            FileInputStream fileStream = new FileInputStream(file);
            byte[] tmp = new byte[1024];
            int len = 0;



//            while ((len = fileStream.read(tmp)) != -1) {

//                arg0.addTextNode(Base64.encode(tmp,0,len));
//                System.out.println(Base64.encode(tmp,0,len));
//                data.addTextNode("data");
//            }

//            arg0.setAttribute("xsi:type","SOAP-ENC:string");
//            arg0.addTextNode("AAAAAAAAAAAAAAAA");
//
//            SOAPElement arg1 = bodyElement.addChildElement("arg1");
//            arg1.setAttribute("xsi:type","SOAP-ENC:string");
//            arg1.addTextNode("BBBBBBBBBBBBBBBBBBBB");

            MimeHeaders hd = message.getMimeHeaders();
            hd.addHeader("SOAPAction", "http://just-a-uri.org/");

            message.saveChanges();


//            message.getSOAPPart().getContent().

            System.out.println("REQUEST:");
//            Display Request Message
            displayMessage(message);

            System.out.println("\n\n");

            SOAPConnection conn = SOAPConnectionFactory.newInstance().createConnection();
//            SOAPMessage response = conn.call(message, endpoint);
//            
//            System.out.println("RESPONSE:");
//            displayMessage(response);
//            
//            result = (String)call.invoke(args);


//            java.text.MessageFormat d = new java.text.MessageFormat("PassernsadcsS");
//            String soapMesgTemplate = d.format(args);
//            System.out.println(soapMesgTemplate);



        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public boolean deleteFile(String endpoint, String file) throws MalformedURLException, ServiceException, RemoteException {

        Service service = null;
        Call call = null;
        String result = null;
        File f = new File(file);

        service = new Service();

        call = (Call) service.createCall();
        call.setTargetEndpointAddress(new URL(endpoint + "TestWS"));
        call.setOperationName(new QName("deleteFile"));
        Object[] args = {new String(file)};

        return (Boolean) call.invoke(args);
    }

    public void displayMessage(SOAPMessage message) throws Exception {
        TransformerFactory tFact = TransformerFactory.newInstance();
        Transformer transformer = tFact.newTransformer();
        Source src = message.getSOAPPart().getContent();
        StreamResult result = new StreamResult(System.out);
        transformer.transform(src, result);
    }

    // converting back with data output stream
    private int[] bytesToInt(byte[] bytes) {
        int[] intBytes = new int[bytes.length];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return bb.asIntBuffer().array();
    }

    private StreamConfig getConf(URI source, String protocol) {
        StreamConfig conf = new StreamConfig();
        conf.displaySpeed = false;
        conf.protocol = protocol;

        if (protocol.equals("HTTP")) {
            conf.fileRequest = source.getPath();
            conf.host = "http://" + source.getHost();
            conf.port = source.getPort();
        } else {
            conf.host = source.getHost();
            conf.port = 8199;
        }

        conf.loggLevel = "ALL";

        conf.maxBufferSize = 131072;
        conf.ecnryptData = false;
        conf.authenticate = false;
        conf.displaySpeed = true;
        conf.saveLogs = false;

        return conf;
    }
}
