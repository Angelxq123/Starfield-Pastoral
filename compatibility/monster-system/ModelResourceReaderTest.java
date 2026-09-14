package com.stardew.craft.monster;

import com.stardew.craft.block.shape.ModelResourceReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelResourceReaderTest {
    private static ByteArrayInputStream text(String value){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
    @Test void partialFileThenRealModelDoesNotBecomeAnEmptyCollision(){
        var calls=new AtomicInteger();
        var model=ModelResourceReader.read("assets/test/stone.json",path->switch(calls.getAndIncrement()){
            case 0->text("{\"parts\":[");case 1->null;
            default->text("{\"parts\":[{\"from\":[0,0,0],\"to\":[16,12,16]}]}");
        });
        assertEquals(3,calls.get());assertEquals(1,model.getAsJsonArray("parts").size());
    }
    @Test void transientIoFailureRecoversWithoutReplacingTheAuthoredModel(){
        var calls=new AtomicInteger();
        var model=ModelResourceReader.read("model.json",path->{if(calls.getAndIncrement()==0)throw new IOException("resource copy interrupted");return text("{\"material\":\"approved\"}");});
        assertEquals("approved",model.get("material").getAsString());assertEquals(2,calls.get());
    }
    @Test void permanentlyBrokenModelThrowsItsPathAndOriginalCauseInsteadOfNull(){
        var calls=new AtomicInteger();
        var error=assertThrows(IllegalStateException.class,()->ModelResourceReader.read("assets/test/broken.json",path->{calls.incrementAndGet();return text("{");}));
        assertTrue(error.getMessage().contains("assets/test/broken.json"));assertNotNull(error.getCause());assertEquals(3,calls.get());
    }
    @Test void absentOptionalParentRemainsAbsentAndDoesNotFabricateGeometry(){
        assertNull(ModelResourceReader.read("builtin/optional.json",path->null));
    }
}
