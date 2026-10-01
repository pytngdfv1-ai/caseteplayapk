package com.mixcasete.app.ui;

import com.mixcasete.app.player.PlayerViewModel;
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
public final class TvPresentationActivity_MembersInjector implements MembersInjector<TvPresentationActivity> {
  private final Provider<PlayerViewModel> vmProvider;

  public TvPresentationActivity_MembersInjector(Provider<PlayerViewModel> vmProvider) {
    this.vmProvider = vmProvider;
  }

  public static MembersInjector<TvPresentationActivity> create(
      Provider<PlayerViewModel> vmProvider) {
    return new TvPresentationActivity_MembersInjector(vmProvider);
  }

  @Override
  public void injectMembers(TvPresentationActivity instance) {
    injectVm(instance, vmProvider.get());
  }

  @InjectedFieldSignature("com.mixcasete.app.ui.TvPresentationActivity.vm")
  public static void injectVm(TvPresentationActivity instance, PlayerViewModel vm) {
    instance.vm = vm;
  }
}
