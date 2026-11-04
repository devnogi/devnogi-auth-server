package until.the.eternity.das.search.dto;

public record UserSearchResponse(String type, Long userId, String nickname, String serverName) {

    public static UserSearchResponse from(UserSearchSnapshot snapshot) {
        return new UserSearchResponse(
                "USER", snapshot.userId(), snapshot.nickname(), snapshot.serverName());
    }
}
