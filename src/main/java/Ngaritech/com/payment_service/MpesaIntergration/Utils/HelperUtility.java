package Ngaritech.com.payment_service.MpesaIntergration.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;


@Slf4j
public class HelperUtility {
    public static String toBase64String(String value){
        byte[] data = value.getBytes(StandardCharsets.ISO_8859_1);
        return Base64.getEncoder().encodeToString(data);
    }

    public static String toJson(Object object){
        try{
            return new ObjectMapper().writeValueAsString(object);
        }catch (JsonProcessingException exception){
            return null;
        }
    }

    public static String getTransactionUniqueNumber(){
        String transactionNumber = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
        log.info(String.format("Transaction Number: %s", transactionNumber));

        return transactionNumber;
    }

    public static String getStkPushPassword(String shortCode, String passKey, String timeStamp){
        String concatenatedString = String.format("%s%s%s", shortCode, passKey, timeStamp);
        return toBase64String(concatenatedString);
    }

    public static String getTransactionTimestamp(){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        return dateFormat.format(new Date());
    }

    @SneakyThrows
    public static String getSecurityCredentials(String initiatorPassword) {
        String encryptedPassword;

        try {
            byte[] input = initiatorPassword.getBytes();

            Resource resource = new ClassPathResource("SandboxCertificate.cer");
            InputStream inputStream = resource.getInputStream();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(inputStream);
            PublicKey pk = certificate.getPublicKey();
            cipher.init(Cipher.ENCRYPT_MODE, pk);

            byte[] cipherText = cipher.doFinal(input);

            // Convert the resulting encrypted byte array into a string using base64 encoding
            encryptedPassword = Base64.getEncoder().encodeToString(cipherText).trim();
            return encryptedPassword;
        } catch (NoSuchAlgorithmException | CertificateException | InvalidKeyException | NoSuchPaddingException |
                 IllegalBlockSizeException | BadPaddingException e) {
            log.error(String.format("Error generating security credentials ->%s", e.getLocalizedMessage()));
            throw e;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }



}
