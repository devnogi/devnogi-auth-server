package until.the.eternity.das.hero;

import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import until.the.eternity.das.common.application.S3Service;

@Service
@RequiredArgsConstructor
public class HeroMedia {
    private final S3Service storage;

    public String upload(MultipartFile file) {
        validate(file);
        String url = storage.uploadImage(file, "hero");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) storage.deleteImageQuietly(url);
                        }
                    });
        }
        return url;
    }

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10L * 1024 * 1024)
            throw HeroException.invalid("10 MiB 이하의 JPEG 또는 PNG 이미지가 필요합니다.");
        String name = file.getOriginalFilename();
        if (name == null || !name.matches("[\\p{L}\\p{N} ._()-]{1,180}\\.(?i:png|jpe?g)"))
            throw HeroException.invalid("JPEG 또는 PNG 파일 이름을 사용해 주세요.");
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw HeroException.invalid("이미지를 읽을 수 없습니다.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"PNG".equalsIgnoreCase(format) && !"JPEG".equalsIgnoreCase(format))
                    throw HeroException.invalid("JPEG 또는 PNG 이미지만 사용할 수 있습니다.");
                reader.setInput(input);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width < 1 || height < 1 || width * height > 24_000_000L)
                    throw HeroException.invalid("이미지는 2,400만 화소 이하여야 합니다.");
                if (reader.read(0) == null) throw HeroException.invalid("이미지를 읽을 수 없습니다.");
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw HeroException.invalid("손상된 이미지입니다.");
        }
    }
}
