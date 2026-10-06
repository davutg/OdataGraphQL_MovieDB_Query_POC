package dev.poc;
import dev.poc.core.CursorCodec;
import dev.poc.core.PolicyException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CursorCodecTest {
 @Test void bindsContinuationToQuery(){CursorCodec codec=new CursorCodec();String f=codec.fingerprint("genre=Drama|rating-desc");String t=codec.encode(50,50,f);assertThat(codec.decode(t,f).offset()).isEqualTo(50);assertThatThrownBy(()->codec.decode(t,codec.fingerprint("other-query"))).isInstanceOf(PolicyException.class);}
 @Test void detectsTampering(){CursorCodec codec=new CursorCodec();String f=codec.fingerprint("q"),t=codec.encode(50,50,f);assertThatThrownBy(()->codec.decode("A"+t.substring(1),f)).isInstanceOf(PolicyException.class);}
 @Test void restartInvalidatesCursor(){CursorCodec a=new CursorCodec();CursorCodec b=new CursorCodec();String f=a.fingerprint("q");assertThatThrownBy(()->b.decode(a.encode(50,50,f),f)).isInstanceOf(PolicyException.class);}
}
