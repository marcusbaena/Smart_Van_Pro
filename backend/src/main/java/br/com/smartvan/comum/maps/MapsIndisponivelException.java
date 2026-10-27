package br.com.smartvan.comum.maps;

public class MapsIndisponivelException extends RuntimeException {
    public MapsIndisponivelException(String mensagem) { super(mensagem); }
    public MapsIndisponivelException(String mensagem, Throwable causa) { super(mensagem, causa); }
}
