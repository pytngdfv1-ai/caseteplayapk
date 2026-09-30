package com.mixcasete.app.player;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class PlaybackService_MembersInjector implements MembersInjector<PlaybackService> {
  private final Provider<AudioEngine> engineProvider;

  public PlaybackService_MembersInjector(Provider<AudioEngine> engineProvider) {
    this.engineProvider = engineProvider;
  }

  public static MembersInjector<PlaybackService> create(Provider<AudioEngine> engineProvider) {
    return new PlaybackService_MembersInjector(engineProvider);
  }

  @Override
  public void injectMembers(PlaybackService instance) {
    injectEngine(instance, engineProvider.get());
  }

  @InjectedFieldSignature("com.mixcasete.app.player.PlaybackService.engine")
  public static void injectEngine(PlaybackService instance, AudioEngine engine) {
    instance.engine = engine;
  }
}
