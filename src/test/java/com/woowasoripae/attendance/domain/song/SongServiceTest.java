package com.woowasoripae.attendance.domain.song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.song.dto.SongCreateRequest;
import com.woowasoripae.attendance.web.song.dto.SongDetailResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 곡 등록/배정 로직. 참여자 표시 순서(보컬 > 세션 > 카혼 > 그 외)가 이 서비스의 핵심 규칙이라
 * 리포지토리가 어떤 순서로 돌려주든 항상 같은 순서로 정렬되는지를 중점적으로 본다.
 */
@ExtendWith(MockitoExtension.class)
class SongServiceTest {

    @Mock
    private SongRepository songRepository;
    @Mock
    private SongMemberRepository songMemberRepository;
    @Mock
    private MemberRepository memberRepository;

    private SongService songService;

    private Member vocal;
    private Member session;
    private Member cajon;
    private Member manager;

    @BeforeEach
    void setUp() {
        songService = new SongService(songRepository, songMemberRepository, memberRepository);

        vocal = memberWithId(1L, "손예은", "보컬");
        session = memberWithId(2L, "지용혁", "세션");
        cajon = memberWithId(3L, "엄태우", "카혼");
        manager = memberWithId(4L, "이승희", "매니저");
    }

    private Member memberWithId(Long id, String name, String part) {
        Member member = new Member(name, null, part);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Song songWithId(Long id, String title) {
        Song song = new Song(title, null);
        ReflectionTestUtils.setField(song, "id", id);
        return song;
    }

    /** 리포지토리가 돌려줄 song_member 배정 목록을 흉내낸다. */
    private void givenAssignedMembers(Long songId, Song song, Member... members) {
        List<SongMember> assignments = List.of(members).stream()
                .map(member -> new SongMember(song, member))
                .toList();
        given(songMemberRepository.findBySongId(songId)).willReturn(assignments);
    }

    @Nested
    @DisplayName("참여자 정렬")
    class MemberOrdering {

        @Test
        @DisplayName("리포지토리 순서와 무관하게 보컬 > 세션 > 카혼 순으로 정렬한다")
        void sortsByPartOrder() {
            Song song = songWithId(10L, "고열");
            given(songRepository.findById(10L)).willReturn(Optional.of(song));
            // 일부러 뒤섞인 순서로 돌려준다.
            givenAssignedMembers(10L, song, cajon, session, vocal);

            SongDetailResponse response = songService.getSongDetail(10L);

            assertThat(response.members())
                    .extracting(m -> m.part())
                    .containsExactly("보컬", "세션", "카혼");
        }

        @Test
        @DisplayName("정렬 목록에 없는 파트(매니저 등)는 맨 뒤로 밀린다")
        void unknownPartGoesLast() {
            Song song = songWithId(11L, "곰팡이");
            given(songRepository.findById(11L)).willReturn(Optional.of(song));
            givenAssignedMembers(11L, song, manager, session, vocal);

            SongDetailResponse response = songService.getSongDetail(11L);

            assertThat(response.members())
                    .extracting(m -> m.part())
                    .containsExactly("보컬", "세션", "매니저");
        }

        @Test
        @DisplayName("같은 파트끼리는 기존 배정 순서를 유지한다")
        void sameParkKeepsInsertionOrder() {
            Member secondSession = memberWithId(5L, "김유미", "세션");
            Song song = songWithId(12L, "밤산책");
            given(songRepository.findById(12L)).willReturn(Optional.of(song));
            givenAssignedMembers(12L, song, session, secondSession, vocal);

            SongDetailResponse response = songService.getSongDetail(12L);

            assertThat(response.members())
                    .extracting(m -> m.name())
                    .containsExactly("손예은", "지용혁", "김유미");
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("곡을 저장하고 요청한 부원들을 모두 배정한다")
        void savesSongAndAssignsMembers() {
            Song saved = songWithId(20L, "편지");
            given(songRepository.save(any())).willReturn(saved);
            given(memberRepository.findById(1L)).willReturn(Optional.of(vocal));
            given(memberRepository.findById(2L)).willReturn(Optional.of(session));
            givenAssignedMembers(20L, saved, vocal, session);

            SongDetailResponse response = songService.create(
                    new SongCreateRequest("편지", null, List.of(1L, 2L)));

            assertThat(response.title()).isEqualTo("편지");
            assertThat(response.members()).hasSize(2);
            verify(songMemberRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        @DisplayName("memberIds가 null이면 참여자 없이 곡만 만든다")
        void allowsNullMemberIds() {
            Song saved = songWithId(21L, "나였으면");
            given(songRepository.save(any())).willReturn(saved);
            given(songMemberRepository.findBySongId(21L)).willReturn(List.of());

            SongDetailResponse response = songService.create(
                    new SongCreateRequest("나였으면", null, null));

            assertThat(response.members()).isEmpty();
            verify(songMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 부원을 배정하면 404를 던진다")
        void throwsNotFoundForUnknownMember() {
            given(songRepository.save(any())).willReturn(songWithId(22L, "도망"));
            given(memberRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> songService.create(
                    new SongCreateRequest("도망", null, List.of(99L))))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("addMember / removeMember")
    class ManageMembers {

        @Test
        @DisplayName("아직 배정되지 않은 부원은 새로 배정한다")
        void addsNewMember() {
            Song song = songWithId(30L, "그러나");
            given(songRepository.findById(30L)).willReturn(Optional.of(song));
            given(songMemberRepository.existsBySongIdAndMemberId(30L, 2L)).willReturn(false);
            given(memberRepository.findById(2L)).willReturn(Optional.of(session));
            givenAssignedMembers(30L, song, vocal, session);

            songService.addMember(30L, 2L);

            verify(songMemberRepository).save(any());
        }

        @Test
        @DisplayName("이미 배정된 부원을 다시 추가해도 중복 저장하지 않는다")
        void doesNotDuplicateExistingMember() {
            Song song = songWithId(31L, "그러나");
            given(songRepository.findById(31L)).willReturn(Optional.of(song));
            given(songMemberRepository.existsBySongIdAndMemberId(31L, 1L)).willReturn(true);
            givenAssignedMembers(31L, song, vocal);

            songService.addMember(31L, 1L);

            verify(songMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("배정된 적 없는 부원을 제거해도 예외 없이 넘어간다")
        void removingUnassignedMemberIsNoop() {
            Song song = songWithId(32L, "그러나");
            given(songRepository.findById(32L)).willReturn(Optional.of(song));
            given(songMemberRepository.findBySongIdAndMemberId(32L, 4L)).willReturn(Optional.empty());
            givenAssignedMembers(32L, song, vocal);

            songService.removeMember(32L, 4L);

            verify(songMemberRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName("존재하지 않는 곡을 조회하면 404를 던진다")
    void throwsNotFoundForUnknownSong() {
        given(songRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> songService.getSongDetail(999L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
