package until.the.eternity.das.search.infrastructure;

import java.util.List;
import java.util.Optional;
import until.the.eternity.das.search.dto.UserSearchSnapshot;

public interface UserSearchSnapshotStore {

    Optional<List<UserSearchSnapshot>> read();

    boolean replace(List<UserSearchSnapshot> snapshot);
}
