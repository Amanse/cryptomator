package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class UnlockVault {

	private final CloudRepository cloudRepository;
	private final VaultOrUnlockToken vaultOrUnlockToken;
	private final String password;

	private volatile boolean cancelled;
	private final Flag cancelledFlag = new Flag() {
		@Override
		public boolean get() {
			return cancelled;
		}
	};

	public UnlockVault(CloudRepository cloudRepository, @Parameter VaultOrUnlockToken vaultOrUnlockToken, @Parameter String password) {
		this.cloudRepository = cloudRepository;
		this.vaultOrUnlockToken = vaultOrUnlockToken;
		this.password = password;
	}

	public void onCancel() {
		cancelled = true;
	}

	public Cloud execute() throws BackendException {
		if (vaultOrUnlockToken.getVault().isPresent()) {
			return cloudRepository.unlock(vaultOrUnlockToken.getVault().get(), password, cancelledFlag);
		} else {
			return cloudRepository.unlock(vaultOrUnlockToken.getUnlockToken().get(), password, cancelledFlag);
		}
	}

}
