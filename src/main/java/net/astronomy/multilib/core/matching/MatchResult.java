package net.astronomy.multilib.core.matching;

public sealed interface MatchResult permits MatchResult.Success, MatchResult.Failure {
    record Success(MatchData data) implements MatchResult {}
    record Failure(MatchFailureReport report) implements MatchResult {}
}
