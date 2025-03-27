package com.example.snsserver.domain.post.service;

import com.example.snsserver.domain.like.repository.LikeRepository;
import com.example.snsserver.domain.post.repository.PostRepository;
import com.example.snsserver.domain.auth.entity.Member;
import com.example.snsserver.domain.post.entity.Post;
import com.example.snsserver.dto.post.request.PostRequestDto;
import com.example.snsserver.dto.post.response.PostResponseDto;
import com.example.snsserver.domain.auth.service.MemberService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final MemberService memberService;
    private final LikeRepository likeRepository;

    @Value("${file.upload-dir:/uploads}")
    private String uploadDir;

    @Transactional
    public PostResponseDto createPost(PostRequestDto requestDto, MultipartFile file) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberService.getMemberByUsername(username);
        if (member == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + username);
        }

        File dir = new File(uploadDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                log.error("Failed to create directory: {}", uploadDir);
                throw new IllegalStateException("Failed to create upload directory: " + uploadDir);
            }
        }

        String filePath = null;
        if (file != null && !file.isEmpty()) {
            try {
                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                File destFile = new File(dir, fileName);
                log.info("Saving file to: {}", destFile.getAbsolutePath());
                file.transferTo(destFile);
                filePath = destFile.getAbsolutePath();
            } catch (IOException e) {
                log.error("Failed to save file: {}", e.getMessage(), e);
                throw new IllegalStateException("File upload failed: " + e.getMessage(), e);
            }
        } else if (file != null) {
            log.warn("Received empty file from request");
        }

        Post post = Post.builder()
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .filePath(filePath)
                .member(member)
                .createdAt(LocalDateTime.now())
                .build();

        Post savedPost = postRepository.save(post);
        return mapToResponseDto(savedPost);
    }

    @Transactional(readOnly = true)
    public List<PostResponseDto> getAllPosts() {
        return postRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PostResponseDto getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물이 존재하지 않습니다."));
        return mapToResponseDto(post);
    }

    @Transactional
    public PostResponseDto updatePost(Long postId, PostRequestDto requestDto, MultipartFile file) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물이 존재하지 않습니다."));

        if (!post.getMember().getUsername().equals(username)) {
            throw new SecurityException("본인의 게시물만 수정할 수 있습니다.");
        }

        String filePath = post.getFilePath();
        if (file != null && !file.isEmpty()) {
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    log.error("Failed to create directory: {}", uploadDir);
                    throw new IllegalStateException("Failed to create upload directory: " + uploadDir);
                }
            }
            try {
                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                File destFile = new File(dir, fileName);
                log.info("Updating file to: {}", destFile.getAbsolutePath());
                file.transferTo(destFile);
                filePath = destFile.getAbsolutePath();
            } catch (IOException e) {
                log.error("Failed to update file: {}", e.getMessage(), e);
                throw new IllegalStateException("File update failed: " + e.getMessage(), e);
            }
        } else if (file != null) {
            log.warn("Received empty file from request");
        }

        post = post.toBuilder()
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .filePath(filePath)
                .build();

        postRepository.save(post);
        return mapToResponseDto(post);
    }

    @Transactional
    public void deletePost(Long postId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물이 존재하지 않습니다."));

        if (!post.getMember().getUsername().equals(username)) {
            throw new SecurityException("본인의 게시물만 삭제할 수 있습니다.");
        }

        if (post.getFilePath() != null) {
            File file = new File(post.getFilePath());
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    log.warn("Failed to delete file: {}", post.getFilePath());
                }
            }
        }

        postRepository.delete(post);
    }

    private PostResponseDto mapToResponseDto(Post post) {
        return PostResponseDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .filePath(post.getFilePath())
                .username(post.getMember().getUsername())
                .createdAt(post.getCreatedAt())
                .likeCount(likeRepository.countByPostId(post.getId())) // 수정
                .commentCount(post.getComments() != null ? post.getComments().size() : 0)
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("Java temp directory: {}", System.getProperty("java.io.tmpdir"));
    }
}